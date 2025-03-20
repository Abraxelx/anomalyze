package com.halilsahin.anomalydetector.service;

import lombok.RequiredArgsConstructor;
import org.apache.spark.ml.clustering.KMeans;
import org.apache.spark.ml.clustering.KMeansModel;
import org.apache.spark.ml.feature.StandardScaler;
import org.apache.spark.ml.feature.StandardScalerModel;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class ModelTrainer {

    private static final Logger logger = LoggerFactory.getLogger(ModelTrainer.class);

    @Value("${model.path}")
    private String modelPath;

    @Value("${scaler.path}")
    private String scalerPath;

    public void trainModel(Dataset<Row> features, VectorAssembler assembler) {
        logger.info("Model eğitimi başlıyor...");
        Dataset<Row> assembledData = assembler.transform(features);

        StandardScaler scaler = new StandardScaler()
                .setInputCol("raw_features")
                .setOutputCol("features")
                .setWithStd(true)
                .setWithMean(true);
        StandardScalerModel scalerModel = scaler.fit(assembledData);
        Dataset<Row> scaledData = scalerModel.transform(assembledData);

        KMeans kmeans = new KMeans()
                .setK(3)
                .setMaxIter(50)
                .setSeed(1L)
                .setFeaturesCol("features")
                .setPredictionCol("prediction");

        KMeansModel model = kmeans.fit(scaledData);

        try {
            model.write().overwrite().save(modelPath);
            scalerModel.write().overwrite().save(scalerPath);
            logger.info("Model ve scaler kaydedildi: {}, {}", modelPath, scalerPath);
        } catch (IOException e) {
            logger.error("Model kaydetme hatası: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
}