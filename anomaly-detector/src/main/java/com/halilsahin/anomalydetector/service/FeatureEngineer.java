package com.halilsahin.anomalydetector.service;

import lombok.RequiredArgsConstructor;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static org.apache.spark.sql.functions.*;

@Service
@RequiredArgsConstructor
public class FeatureEngineer {

    private static final Logger logger = LoggerFactory.getLogger(FeatureEngineer.class);

    private final VectorAssembler assembler = new VectorAssembler()
            .setInputCols(new String[]{"total_logs", "error_count", "error_rate", "avg_message_length"})
            .setOutputCol("raw_features");

    public Dataset<Row> prepareFeatures(Dataset<Row> logs) {
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
        return features;
    }

    public VectorAssembler getAssembler() {
        return assembler;
    }
}