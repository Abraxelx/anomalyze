package com.halilsahin.anomalydetector.service;

import com.halilsahin.anomalydetector.model.Alert;
import lombok.RequiredArgsConstructor;
import org.apache.spark.sql.*;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.types.StructType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import static org.apache.spark.sql.functions.*;

@Service
@RequiredArgsConstructor
public class AnomalyDetectorService {

    private final SparkSession sparkSession;
    private final RestTemplate restTemplate;

    @Value("${kafka.bootstrap-servers}")
    private String kafkaBootstrapServers;

    @Value("${kafka.topic.logs}")
    private String logsTopic;

    @Value("${alert.service.url}")
    private String alertServiceUrl;

    @Value("${anomaly.error.threshold}")
    private int errorThreshold;

    @Value("${anomaly.window.minutes}")
    private int windowMinutes;

    private StreamingQuery query;
    
    // İstatistik bilgileri
    private long receivedLogCount = 0;
    private long processedWindowCount = 0;
    private long detectedAnomalyCount = 0;

    @PostConstruct
    public void startDetection() {
        try {
            System.out.println("ANOMALİ DEDEKTÖRÜ BAŞLATILIYOR - " + kafkaBootstrapServers + " - Topic: " + logsTopic);
            
            // Checkpoint dizini oluştur
            String checkpointDir = System.getProperty("user.home") + "/anomalyze-checkpoint";
            System.out.println("Checkpoint dizini: " + checkpointDir);
            
            // Kafka'dan log verilerini al
            Dataset<Row> kafkaStream = sparkSession
                    .readStream()
                    .format("kafka")
                    .option("kafka.bootstrap.servers", kafkaBootstrapServers)
                    .option("subscribe", logsTopic)
                    .option("startingOffsets", "latest")
                    .load();
            
            // Log şemasına göre dönüştür
            StructType logSchema = createLogSchema();
            Dataset<Row> logs = kafkaStream
                    .selectExpr("CAST(value AS STRING) as json")
                    .select(functions.from_json(col("json"), logSchema).as("log"))
                    .select("log.*");
            
            // Pencere bazında gruplama ve ERROR oranı hesaplama
            Dataset<Row> errorRates = logs
                    .withWatermark("timestamp", windowMinutes + " minutes")
                    .groupBy(
                            window(col("timestamp"), windowMinutes + " minutes"),
                            col("serviceName")
                    )
                    .agg(
                            count("*").as("total_logs"),
                            count(when(col("level").equalTo("ERROR"), 1)).as("error_count")
                    )
                    .withColumn("error_rate", 
                            round(col("error_count").multiply(100).divide(col("total_logs")), 2));
            
            System.out.println("Hata oranı hesaplama için group by işlemi yapılandırıldı");
            
            // Hesaplanan tüm pencere sonuçlarını görüntüle (anomali olmasa bile)
            errorRates.writeStream()
                .outputMode("update")
                .foreachBatch((batchDF, batchId) -> {
                    if (!batchDF.isEmpty()) {
                        processedWindowCount += batchDF.count();
                        
                        // Anomali tespiti - Error rate threshold'u geçenleri kontrol et
                        Dataset<Row> anomalies = batchDF.filter(col("error_rate").gt(errorThreshold));
                        
                        if (!anomalies.isEmpty()) {
                            long anomalyCount = anomalies.count();
                            detectedAnomalyCount += anomalyCount;
                            
                            // Her bir anomali için alert gönder
                            anomalies.foreach(row -> {
                                String serviceName = row.getString(row.fieldIndex("serviceName"));
                                double errorRate = row.getDouble(row.fieldIndex("error_rate"));
                                sendAlert(serviceName, errorRate);
                            });
                        }
                    }
                })
                .trigger(org.apache.spark.sql.streaming.Trigger.ProcessingTime("10 seconds"))
                .start();

            // Artık buradaki ayrı anomali tespiti query'sine ihtiyacımız yok
            // Yukarıdaki foreachBatch içinde zaten anomali tespiti ve alert gönderimi yapılıyor
            query = errorRates
                    .writeStream()
                    .outputMode("update")  
                    .trigger(org.apache.spark.sql.streaming.Trigger.ProcessingTime("10 seconds"))
                    .foreachBatch((batch, id) -> {
                        // Boş bırakıyoruz, sadece query referansını tutmak için
                    })
                    .start();

            // Streaming Query'yi dinleyen ayrı bir thread başlat
            new Thread(() -> {
                try {
                    System.out.println("Anomali tespiti aktif - Çıkmak için CTRL+C");
                    query.awaitTermination();
                } catch (Exception e) {
                    System.out.println("Stream sonlandırıldı: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            System.out.println("Anomali tespiti başlatılırken hata oluştu: " + e.getMessage());
            e.printStackTrace();
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

    private void sendAlert(String serviceName, double errorRate) {
        try {
            System.out.println("\n>>> ANOMALİ TESPİT EDİLDİ - ALERT GÖNDERİLİYOR");
            System.out.println("Servis: " + serviceName + ", Hata Oranı: %" + errorRate);
            
            // Alert nesnesi oluştur
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("serviceName", serviceName);
            metadata.put("errorRate", errorRate);
            metadata.put("threshold", errorThreshold);

            Alert alert = Alert.builder()
                    .type("HIGH_ERROR_RATE")
                    .severity("HIGH")
                    .source("anomaly-detector")
                    .timestamp(java.time.LocalDateTime.now())
                    .description(String.format("%s servisinde yüksek hata oranı: %.2f%%", serviceName, errorRate))
                    .metadata(metadata)
                    .build();
                    
            // Jackson ObjectMapper ile JSON'a dönüştür
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
            
            String json = mapper.writeValueAsString(alert);
            
            // HTTP isteği gönder
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
                
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(alertServiceUrl + "/api/alerts"))
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(5))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                .build();
                
            java.net.http.HttpResponse<String> response = client.send(
                request, 
                java.net.http.HttpResponse.BodyHandlers.ofString()
            );
                
            if (response.statusCode() >= 400) {
                System.out.println("!!! ALERT GÖNDERİLEMEDİ. HTTP Hata: " + response.statusCode());
                System.out.println("Hata detayı: " + response.body());
            } else {
                System.out.println("✓ ALERT BAŞARIYLA GÖNDERİLDİ");
            }
        } catch (Exception e) {
            System.out.println("!!! ALERT GÖNDERİLİRKEN HATA: " + e.getMessage());
        }
    }
} 