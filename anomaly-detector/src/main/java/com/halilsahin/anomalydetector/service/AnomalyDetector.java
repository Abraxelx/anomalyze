package com.halilsahin.anomalydetector.service;

import lombok.RequiredArgsConstructor;
import org.apache.spark.ml.clustering.KMeansModel;
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

import java.nio.file.Paths;

import static org.apache.spark.sql.functions.*;

@Service
@RequiredArgsConstructor
public class AnomalyDetector {

    private static final Logger logger = LoggerFactory.getLogger(AnomalyDetector.class);

    private final SparkSession sparkSession;
    private final AlertSender alertSender;

    @Value("${spring.kafka.bootstrap-servers}")
    private String kafkaBootstrapServers;

    @Value("${kafka.topic.logs}")
    private String logsTopic;

    @Value("${anomaly.window.minutes}")
    private int windowMinutes;

    @Value("${spark.checkpoint.dir}")
    private String checkpointDir;

    @Value("${model.path}")
    private String modelPath;

    @Value("${scaler.path}")
    private String scalerPath;

    public void startDetection() {
        logger.info("Anomali tespiti başlatılıyor...");
        try {
            KMeansModel model = KMeansModel.load(modelPath);
            StandardScalerModel scalerModel = StandardScalerModel.load(scalerPath);

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

            VectorAssembler assembler = new VectorAssembler()
                    .setInputCols(new String[]{"total_logs", "error_count", "error_rate", "avg_message_length"})
                    .setOutputCol("raw_features");

            Dataset<Row> assembledData = assembler.transform(features);
            Dataset<Row> scaledData = scalerModel.transform(assembledData);
            Dataset<Row> predictions = model.transform(scaledData);

            String checkpointLocation = Paths.get(checkpointDir, "anomaly-detection-stream").toString();
            StreamingQuery query = predictions
                    .writeStream()
                    .outputMode("update")
                    .trigger(org.apache.spark.sql.streaming.Trigger.ProcessingTime("15 seconds"))
                    .foreachBatch((batchDF, batchId) -> {
                        if (!batchDF.isEmpty()) {
                            batchDF.cache();

                            // Orta risk (prediction == 1)
                            Dataset<Row> mediumRisk = batchDF.filter(
                                    col("prediction").equalTo(1)
                                            .and(col("error_rate").gt(5.0))
                                            .and(col("total_logs").gt(10))
                            );
                            // Yüksek risk (prediction == 2)
                            Dataset<Row> highRisk = batchDF.filter(
                                    col("prediction").equalTo(2)
                                            .and(col("error_rate").gt(15.0)) //%15
                                            .and(col("total_logs").gt(10))
                            );

                            if (!mediumRisk.isEmpty()) {
                                Row[] mediumRows = (Row[]) mediumRisk.collect();
                                for (Row row : mediumRows) {
                                    String serviceName = row.getString(row.fieldIndex("serviceName"));
                                    double errorRate = row.getDouble(row.fieldIndex("error_rate"));
                                    alertSender.sendAlert(serviceName, errorRate, "MEDIUM");
                                }
                            }
                            if (!highRisk.isEmpty()) {
                                Row[] highRows = (Row[]) highRisk.collect();
                                for (Row row : highRows) {
                                    String serviceName = row.getString(row.fieldIndex("serviceName"));
                                    double errorRate = row.getDouble(row.fieldIndex("error_rate"));
                                    alertSender.sendAlert(serviceName, errorRate, "HIGH");
                                }
                            }
                            batchDF.unpersist();
                        }
                    })
                    .option("checkpointLocation", checkpointLocation)
                    .start();

            query.awaitTermination();
        } catch (Exception e) {
            logger.error("Anomali tespiti hatası: {}", e.getMessage());
        }
    }

    private StructType createLogSchema() {
        return new StructType()
                .add("timestamp", "timestamp")
                .add("level", "string")
                .add("message", "string")
                .add("relatedObjectId", "string")
                .add("serviceName", "string");
    }
}