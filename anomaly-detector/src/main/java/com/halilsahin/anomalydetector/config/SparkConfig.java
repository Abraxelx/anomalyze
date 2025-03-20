package com.halilsahin.anomalydetector.config;

import org.apache.spark.SparkConf;
import org.apache.spark.sql.SparkSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

@Configuration
public class SparkConfig {

    @Value("${spark.checkpoint.dir}")
    private String checkpointDir;

    @Bean
    public SparkSession sparkSession() {
        try {
            Files.createDirectories(Paths.get(checkpointDir));
        } catch (IOException e) {
            throw new RuntimeException("Checkpoint dizini oluşturulurken hata: " + checkpointDir, e);
        }

        SparkConf conf = new SparkConf()
                .setAppName("AnomalyDetector")
                .setMaster("local[*]")
                .set("spark.ui.enabled", "false") // Spark UI’yi devre dışı bırak
                .set("spark.sql.streaming.checkpointLocation", checkpointDir)
                .set("spark.driver.host", "localhost")
                .set("spark.driver.extraJavaOptions",
                        "--add-opens=java.base/java.lang=ALL-UNNAMED " +
                                "--add-opens=java.base/java.util=ALL-UNNAMED " +
                                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED")
                .set("spark.executor.extraJavaOptions",
                        "--add-opens=java.base/java.lang=ALL-UNNAMED " +
                                "--add-opens=java.base/java.util=ALL-UNNAMED " +
                                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED");

        SparkSession spark = SparkSession.builder()
                .config(conf)
                .getOrCreate();

        spark.sparkContext().setLogLevel("ERROR");

        return spark;
    }
}