package com.halilsahin.logprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.spark.sql.*;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.StreamingQueryException;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeoutException;

import static org.apache.spark.sql.functions.*;

public class LogProcessorApplication {
    private static final Logger logger = LoggerFactory.getLogger(LogProcessorApplication.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public static void main(String[] args) {
        logger.info("Starting Log Processor Application");
        
        // Spark Session oluştur
        SparkSession spark = SparkSession.builder()
                .appName("Log Processor")
                .master("spark://spark-master:7077") // Docker Compose'daki Spark Master adresi
                .config("spark.sql.streaming.checkpointLocation", "/tmp/checkpoint")
                .getOrCreate();
        
        // Log seviyesi ayarla
        spark.sparkContext().setLogLevel("WARN");
        
        // JSON şemasını tanımla
        StructType schema = DataTypes.createStructType(new StructField[] {
            DataTypes.createStructField("id", DataTypes.StringType, true),
            DataTypes.createStructField("serviceName", DataTypes.StringType, true),
            DataTypes.createStructField("level", DataTypes.StringType, true),
            DataTypes.createStructField("timestamp", DataTypes.StringType, true),
            DataTypes.createStructField("message", DataTypes.StringType, true),
            DataTypes.createStructField("relatedObjectId", DataTypes.StringType, true),
            DataTypes.createStructField("eventType", DataTypes.StringType, true),
            DataTypes.createStructField("contextData", DataTypes.StringType, true)
        });
        
        // Kafka'dan veri oku
        Dataset<Row> kafkaStream = spark
                .readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", "kafka:29092") // Docker Compose'daki Kafka adresi
                .option("subscribe", "logs-topic")
                .option("startingOffsets", "earliest")
                .load();
        
        // JSON verisini parse et
        Dataset<Row> logEvents = kafkaStream
                .selectExpr("CAST(value AS STRING) as json")
                .select(from_json(col("json"), schema).as("data"))
                .select("data.*");
        
        // Log seviyelerine göre sayma
        Dataset<Row> logCounts = logEvents
                .groupBy(
                    window(to_timestamp(col("timestamp"), "yyyy-MM-dd'T'HH:mm:ss"), "1 minute"),
                    col("serviceName"),
                    col("level")
                )
                .count()
                .orderBy("window");
        
        // ERROR loglarının oranını hesapla
        Dataset<Row> errorRatios = logCounts
                .groupBy("window", "serviceName")
                .pivot("level")
                .sum("count")
                .na().fill(0)
                .withColumn("total", 
                    coalesce(col("INFO"), lit(0))
                    .plus(coalesce(col("WARN"), lit(0)))
                    .plus(coalesce(col("ERROR"), lit(0)))
                )
                .withColumn("errorRatio", 
                    when(col("total").equalTo(0), 0)
                    .otherwise(col("ERROR").divide(col("total")).multiply(100))
                )
                .select("window", "serviceName", "INFO", "WARN", "ERROR", "total", "errorRatio");
        
        // Anomali tespiti - ERROR oranı %30'dan fazla ise
        Dataset<Row> anomalies = errorRatios
                .filter(col("errorRatio").gt(30.0))
                .withColumn("anomalyType", lit("High Error Rate"))
                .withColumn("description", 
                    concat(lit("Service "), col("serviceName"), 
                           lit(" has high error rate: "), col("errorRatio"), lit("%"))
                );
        
        try {
            // Konsola yazdır (test için)
            StreamingQuery logCountsQuery = logCounts
                    .writeStream()
                    .outputMode("complete")
                    .format("console")
                    .option("truncate", false)
                    .start();
            
            StreamingQuery errorRatiosQuery = errorRatios
                    .writeStream()
                    .outputMode("complete")
                    .format("console")
                    .option("truncate", false)
                    .start();
            
            StreamingQuery anomaliesQuery = anomalies
                    .writeStream()
                    .outputMode("complete")
                    .format("console")
                    .option("truncate", false)
                    .start();
            
            // Uygulamayı çalışır durumda tut
            spark.streams().awaitAnyTermination();
            
        } catch (StreamingQueryException | TimeoutException e) {
            logger.error("Error in streaming query", e);
        } finally {
            spark.stop();
        }
    }
} 