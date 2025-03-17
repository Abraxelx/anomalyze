package com.halilsahin.dashboard.service;

import com.halilsahin.dashboard.model.Alert;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
public class AlertService {

    private final RestTemplate restTemplate;
    private final String alertServiceUrl;

    public AlertService(RestTemplate restTemplate, 
                      @Value("${services.alert.url:http://localhost:8082}") String alertServiceUrl) {
        this.restTemplate = restTemplate;
        this.alertServiceUrl = alertServiceUrl;
    }

    public List<Alert> getAllAlerts() {
        try {
            ResponseEntity<List<Alert>> response = restTemplate.exchange(
                    alertServiceUrl + "/api/alerts/all",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Alert>>() {}
            );
            return response.getBody();
        } catch (Exception e) {
            System.err.println("Alert servisine erişirken hata oluştu: " + e.getMessage());
            return Collections.emptyList();
        }
    }
} 