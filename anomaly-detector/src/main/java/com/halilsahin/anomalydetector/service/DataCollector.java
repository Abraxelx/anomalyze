package com.halilsahin.anomalydetector.service;

import lombok.RequiredArgsConstructor;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.from_json;

@Service
@RequiredArgsConstructor
public class DataCollector {

    private static final Logger logger = LoggerFactory.getLogger(DataCollector.class);

    private final SparkSession sparkSession;

    @Value("${spring.kafka.bootstrap-servers}")
    private String kafkaBootstrapServers;

    @Value("${kafka.topic.logs}")
    private String logsTopic;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String jdbcUsername;

    @Value("${spring.datasource.password}")
    private String jdbcPassword;

    public Dataset<Row> collectTrainingData(String tableName) {
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

        StructType logSchema = createLogSchema();
        Dataset<Row> logs = kafkaHistoricalData
                .selectExpr("CAST(value AS STRING) as json")
                .select(from_json(col("json"), logSchema).as("log"))
                .select("log.*");

        logs.write()
                .format("jdbc")
                .option("url", jdbcUrl)
                .option("dbtable", tableName)
                .option("user", jdbcUsername)
                .option("password", jdbcPassword)
                .mode("overwrite")
                .save();
        return logs;
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