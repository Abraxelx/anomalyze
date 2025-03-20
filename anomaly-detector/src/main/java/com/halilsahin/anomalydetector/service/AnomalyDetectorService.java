package com.halilsahin.anomalydetector.service;

import lombok.RequiredArgsConstructor;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class AnomalyDetectorService {

    private static final Logger logger = LoggerFactory.getLogger(AnomalyDetectorService.class);

    private final DataCollector dataCollector;
    private final FeatureEngineer featureEngineer;
    private final ModelTrainer modelTrainer;
    private final AnomalyDetector anomalyDetector;

    @PostConstruct
    public void init() {
        try {
            String trainingTable = "training_logs";
            if (!isModelValid()) {
                Dataset<Row> trainingData = dataCollector.collectTrainingData(trainingTable);
                Dataset<Row> features = featureEngineer.prepareFeatures(trainingData);
                modelTrainer.trainModel(features, featureEngineer.getAssembler());
            }
            anomalyDetector.startDetection();
        } catch (Exception e) {
            logger.error("Başlatma hatası: {}", e.getMessage());
            throw new RuntimeException("AnomalyDetectorService başlatılamadı", e);
        }
    }

    private boolean isModelValid() {
        String modelPath = System.getProperty("user.dir") + "/model";
        String scalerPath = System.getProperty("user.dir") + "/scaler";
        return Files.exists(Paths.get(modelPath)) && Files.exists(Paths.get(scalerPath));
    }
}