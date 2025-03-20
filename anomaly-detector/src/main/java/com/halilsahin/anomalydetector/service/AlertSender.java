package com.halilsahin.anomalydetector.service;

import com.halilsahin.anomalydetector.model.Alert;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlertSender {

    private static final Logger logger = LoggerFactory.getLogger(AlertSender.class);

    private final RestTemplate restTemplate;

    @Value("${alert.service.url}")
    private String alertServiceUrl;

    @Retryable(value = {RestClientException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void sendAlert(String serviceName, double errorRate, String severity) {
        logger.info("Alert: serviceName={}, errorRate={}, severity={}", serviceName, errorRate, severity);
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("serviceName", serviceName);
            metadata.put("errorRate", errorRate);

            Alert alert = Alert.builder()
                    .type("HIGH_ERROR_RATE")
                    .severity(severity)
                    .source("anomaly-detector")
                    .timestamp(LocalDateTime.now())
                    .description(String.format("%s servisinde hata oranı: %.2f%%", serviceName, errorRate))
                    .metadata(metadata)
                    .build();

            restTemplate.postForEntity(alertServiceUrl + "/api/alerts", alert, String.class);
        } catch (Exception e) {
            logger.error("Alert hatası: serviceName={}, errorRate={}, severity={}, hata: {}", serviceName, errorRate, severity, e.getMessage());
            throw e;
        }
    }
}