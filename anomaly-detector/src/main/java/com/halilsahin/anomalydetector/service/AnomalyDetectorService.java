package com.halilsahin.anomalydetector.service;

import com.halilsahin.anomalydetector.model.Alert;
import lombok.RequiredArgsConstructor;
import org.apache.spark.ml.clustering.KMeans;
import org.apache.spark.ml.clustering.KMeansModel;
import org.apache.spark.ml.feature.StandardScaler;
import org.apache.spark.ml.feature.StandardScalerModel;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.apache.spark.sql.functions.*;

@Service
@RequiredArgsConstructor
public class AnomalyDetectorService {

    private static final Logger logger = LoggerFactory.getLogger(AnomalyDetectorService.class);

    private final SparkSession sparkSession;
    private final RestTemplate restTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    private String kafkaBootstrapServers;

    @Value("${kafka.topic.logs}")
    private String logsTopic;

    @Value("${alert.service.url}")
    private String alertServiceUrl;

    @Value("${anomaly.window.minutes}")
    private int windowMinutes;

    @Value("${spark.checkpoint.dir}")
    private String checkpointDir;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String jdbcUsername;

    @Value("${spring.datasource.password}")
    private String jdbcPassword;

    private static final String MODEL_PATH = "file:///Users/xelil/git/anomalyze/anomaly-detector/model";
    private static final String SCALER_PATH = "file:///Users/xelil/git/anomalyze/anomaly-detector/scaler";

    // Özellik birleştiriciyi sınıf seviyesinde tanımla
    private final VectorAssembler assembler = new VectorAssembler()
            .setInputCols(new String[]{"total_logs", "error_count", "error_rate", "avg_message_length"})
            .setOutputCol("raw_features");

    @PostConstruct
    public void init() {
        String trainingTable = "training_logs";
        logger.info("AnomalyDetectorService başlatılıyor...");
        try {
            if (!isModelValid()) {
                logger.info("Eğitim başladı...");
                Dataset<Row> trainingData = collectTrainingData(trainingTable);
                logger.info("Eğitim verisi toplandı, özellikler hazırlanıyor...");
                Dataset<Row> features = prepareFeatures(trainingData);
                trainModel(features);
                logger.info("Eğitim bitti.");
            } else {
                logger.info("Model zaten mevcut: {}, eğitim atlanıyor.", MODEL_PATH);
                logExistingModelDetails();
            }
            logger.info("Anomali tespiti başlatılıyor...");
            startDetection();
        } catch (Exception e) {
            logger.error("AnomalyDetectorService başlatılırken hata oluştu: {}", e.getMessage(), e);
            throw new RuntimeException("AnomalyDetectorService başlatılırken hata: " + e.getMessage(), e);
        }
    }

    private boolean isModelValid() {
        logger.info("Model geçerliliği kontrol ediliyor...");
        try {
            java.nio.file.Path modelDir = Paths.get(new java.net.URI(MODEL_PATH));
            java.nio.file.Path scalerDir = Paths.get(new java.net.URI(SCALER_PATH));
            if (!java.nio.file.Files.exists(modelDir) || !java.nio.file.Files.exists(scalerDir)) {
                logger.info("Model veya scaler dizini bulunamadı: {}, {}", MODEL_PATH, SCALER_PATH);
                return false;
            }
            java.nio.file.Path metadataPath = modelDir.resolve("metadata");
            boolean isValid = java.nio.file.Files.exists(metadataPath) && java.nio.file.Files.list(metadataPath).findAny().isPresent();
            logger.info("Model geçerlilik sonucu: {}", isValid);
            return isValid;
        } catch (Exception e) {
            logger.warn("Model geçerliliği kontrol edilirken hata: {}, yeniden eğitim yapılacak.", e.getMessage());
            return false;
        }
    }

    private void logExistingModelDetails() {
        logger.info("Mevcut modelin detayları loglanıyor...");
        try {
            KMeansModel model = KMeansModel.load(MODEL_PATH);
            StandardScalerModel scalerModel = StandardScalerModel.load(SCALER_PATH);
            logger.info("Küme merkezleri:");
            for (org.apache.spark.ml.linalg.Vector center : model.clusterCenters()) {
                logger.info("{}", center.toString());
            }

            Dataset<Row> trainingData = collectTrainingData("training_logs_temp");
            Dataset<Row> features = prepareFeatures(trainingData);
            Dataset<Row> assembledData = assembler.transform(features);
            Dataset<Row> scaledData = scalerModel.transform(assembledData);
            Dataset<Row> predictions = model.transform(scaledData);

            logger.info("Eğitim verisi üzerindeki tahminler, satır sayısı: {}", predictions.count());
            logger.info("Tahmin örnekleri:");
            predictions.show(20, false);
            logger.info("Tahmin dağılımı:");
            predictions.groupBy("prediction").count().show();
        } catch (Exception e) {
            logger.error("Mevcut modelin detayları loglanırken hata: {}", e.getMessage(), e);
        }
    }

    private Dataset<Row> collectTrainingData(String tableName) {
        logger.info("Eğitim verisi toplanıyor...");
        Dataset<Row> kafkaHistoricalData = sparkSession
                .read()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaBootstrapServers)
                .option("subscribe", logsTopic)
                .option("startingOffsets", "earliest")
                .option("maxOffsetsPerTrigger", "2000")
                .option("failOnDataLoss", "false")
                .load();
        logger.info("Kafka’dan ham veri çekildi, satır sayısı: {}", kafkaHistoricalData.count());

        StructType logSchema = createLogSchema();
        Dataset<Row> logs = kafkaHistoricalData
                .selectExpr("CAST(value AS STRING) as json")
                .select(from_json(col("json"), logSchema).as("log"))
                .select("log.*"); // limit(2000) kaldırıldı, tüm veriyi kullan

        logger.info("Eğitim verisi işlendi, satır sayısı: {}", logs.count());
        logger.info("Eğitim verisi dağılımı:");
        logs.groupBy("level").count().show();
        logger.info("Eğitim verisi örnekleri:");
        logs.show(20, false);

        logger.info("Eğitim verisi JDBC’ye yazılıyor...");
        logs.write()
                .format("jdbc")
                .option("url", jdbcUrl)
                .option("dbtable", tableName)
                .option("user", jdbcUsername)
                .option("password", jdbcPassword)
                .mode("overwrite")
                .save();
        logger.info("Eğitim verisi JDBC’ye yazıldı.");
        return logs;
    }

    private Dataset<Row> prepareFeatures(Dataset<Row> logs) {
        logger.info("Özellikler hazırlanıyor...");
        Dataset<Row> features = logs
                .groupBy(
                        window(col("timestamp"), "5 minutes").getField("start").as("window_start"),
                        col("serviceName")
                )
                .agg(
                        count("*").as("total_logs"),
                        count(when(col("level").equalTo("ERROR"), 1)).as("error_count"),
                        avg(length(col("message"))).as("avg_message_length")
                )
                .withColumn("error_rate",
                        round(col("error_count").multiply(100).divide(col("total_logs")), 2));

        logger.info("Özellikler hesaplandı, satır sayısı: {}", features.count());
        logger.info("Özellikler örnekleri:");
        features.show(20, false);
        return features;
    }

    private void trainModel(Dataset<Row> features) {
        logger.info("Model eğitimi başlıyor...");
        Dataset<Row> assembledData = assembler.transform(features);

        // Özellikleri ölçeklendir
        StandardScaler scaler = new StandardScaler()
                .setInputCol("raw_features")
                .setOutputCol("features")
                .setWithStd(true)
                .setWithMean(true);
        StandardScalerModel scalerModel = scaler.fit(assembledData);
        Dataset<Row> scaledData = scalerModel.transform(assembledData);

        KMeans kmeans = new KMeans()
                .setK(2)
                .setMaxIter(20) // Daha iyi kümelenme için iterasyon artırıldı
                .setSeed(1L)
                .setFeaturesCol("features")
                .setPredictionCol("prediction");

        logger.info("Eğitim ilerlemesi: %0");
        KMeansModel model = kmeans.fit(scaledData);
        logger.info("Eğitim ilerlemesi: %100");

        logger.info("Küme merkezleri:");
        for (org.apache.spark.ml.linalg.Vector center : model.clusterCenters()) {
            logger.info("{}", center.toString());
        }

        Dataset<Row> predictions = model.transform(scaledData);
        logger.info("Eğitim verisi üzerindeki tahminler, satır sayısı: {}", predictions.count());
        logger.info("Tahmin örnekleri:");
        predictions.show(20, false);
        logger.info("Tahmin dağılımı:");
        predictions.groupBy("prediction").count().show();

        logger.info("Model ve scaler dosyaya kaydediliyor...");
        try {
            model.write().overwrite().save(MODEL_PATH);
            scalerModel.write().overwrite().save(SCALER_PATH);
            logger.info("Model kaydedildi: {}, Scaler kaydedildi: {}", MODEL_PATH, SCALER_PATH);
        } catch (IOException e) {
            logger.error("Model kaydedilirken hata: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void startDetection() {
        logger.info("Anomali tespiti başlatılıyor...");
        try {
            KMeansModel model = KMeansModel.load(MODEL_PATH);
            StandardScalerModel scalerModel = StandardScalerModel.load(SCALER_PATH);
            logger.info("Model yüklendi: {}, Scaler yüklendi: {}", MODEL_PATH, SCALER_PATH);

            Dataset<Row> kafkaStream = sparkSession.readStream()
                    .format("kafka")
                    .option("kafka.bootstrap.servers", kafkaBootstrapServers)
                    .option("subscribe", logsTopic)
                    .option("startingOffsets", "latest")
                    .load();

            StructType logSchema = createLogSchema();
            Dataset<Row> logs = kafkaStream
                    .selectExpr("CAST(value AS STRING) as json")
                    .select(from_json(col("json"), logSchema).as("log"))
                    .select("log.*");

            Dataset<Row> features = logs
                    .withWatermark("timestamp", windowMinutes + " minutes")
                    .groupBy(
                            window(col("timestamp"), windowMinutes + " minutes").getField("start").as("window_start"),
                            col("serviceName")
                    )
                    .agg(
                            count("*").as("total_logs"),
                            count(when(col("level").equalTo("ERROR"), 1)).as("error_count"),
                            avg(length(col("message"))).as("avg_message_length")
                    )
                    .withColumn("error_rate", round(col("error_count").multiply(100).divide(col("total_logs")), 2));

            Dataset<Row> assembledData = assembler.transform(features);
            Dataset<Row> scaledData = scalerModel.transform(assembledData);
            Dataset<Row> predictions = model.transform(scaledData);

            String checkpointLocation = Paths.get(checkpointDir, "anomaly-detection-stream").toString();
            StreamingQuery query = predictions
                    .writeStream()
                    .outputMode("update")
                    .trigger(org.apache.spark.sql.streaming.Trigger.ProcessingTime("15 seconds"))
                    .foreachBatch((batchDF, batchId) -> {
                        logger.info("Batch {} işlenmeye başlandı.", batchId);
                        if (!batchDF.isEmpty()) {
                            batchDF.cache();
                            logger.info("Batch {} işleniyor, satır sayısı: {}", batchId, batchDF.count());
                            logger.info("Batch verisi:");
                            batchDF.show(20, false);
                            Dataset<Row> anomalies = batchDF.filter(col("prediction").equalTo(1));
                            if (!anomalies.isEmpty()) {
                                logger.info("Anomali tespit edildi, satır sayısı: {}", anomalies.count());
                                logger.info("Anomali verisi:");
                                anomalies.show(20, false);
                                Row[] anomalyRows = (Row[]) anomalies.collect();
                                for (Row row : anomalyRows) {
                                    String serviceName = row.getString(row.fieldIndex("serviceName"));
                                    double errorRate = row.getDouble(row.fieldIndex("error_rate"));
                                    logger.info("Anomali: serviceName={}, errorRate={}", serviceName, errorRate);
                                    sendAlert(serviceName, errorRate);
                                }
                            } else {
                                logger.info("Bu batch’te anomali yok.");
                            }
                            batchDF.unpersist();
                        } else {
                            logger.info("Batch {} boş.", batchId);
                        }
                    })
                    .option("checkpointLocation", checkpointLocation)
                    .start();

            logger.info("Streaming sorgusu başlatıldı, bekleniyor...");
            query.awaitTermination();
        } catch (Exception e) {
            logger.error("Anomali tespiti başlatılırken hata: {}", e.getMessage(), e);
        }
    }

    private StructType createLogSchema() {
        logger.info("Log şeması oluşturuluyor...");
        StructType schema = new StructType()
                .add("timestamp", "timestamp")
                .add("level", "string")
                .add("message", "string")
                .add("relatedObjectId", "string")
                .add("serviceName", "string");
        logger.info("Log şeması oluşturuldu.");
        return schema;
    }

    private void sendAlert(String serviceName, double errorRate) {
        logger.info("Alert gönderiliyor: serviceName={}, errorRate={}", serviceName, errorRate);
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("serviceName", serviceName);
            metadata.put("errorRate", errorRate);

            Alert alert = Alert.builder()
                    .type("HIGH_ERROR_RATE")
                    .severity("HIGH")
                    .source("anomaly-detector")
                    .timestamp(java.time.LocalDateTime.now())
                    .description(String.format("%s servisinde yüksek hata oranı: %.2f%%", serviceName, errorRate))
                    .metadata(metadata)
                    .build();

            restTemplate.postForEntity(alertServiceUrl + "/api/alerts", alert, String.class);
            logger.info("Alert başarıyla gönderildi: serviceName={}, errorRate={}", serviceName, errorRate);
        } catch (Exception e) {
            logger.error("Alert gönderilirken hata: serviceName={}, errorRate={}, hata: {}", serviceName, errorRate, e.getMessage(), e);
        }
    }
}