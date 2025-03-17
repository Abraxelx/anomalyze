package com.halilsahin.dashboard.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SimulatorService {
    
    private final RestTemplate restTemplate;
    
    @Value("${service.order.url}")
    private String orderServiceUrl;
    
    public ServiceStatus getOrderServiceStatus() {
        try {
            Boolean running = restTemplate.getForObject(orderServiceUrl + "/api/simulator/status", Boolean.class);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                orderServiceUrl + "/api/simulator/rates",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> rates = response.getBody();
            int infoRate = rates != null ? ((Number) rates.get("infoRate")).intValue() : 70;
            int warnRate = rates != null ? ((Number) rates.get("warnRate")).intValue() : 20;
            int errorRate = rates != null ? ((Number) rates.get("errorRate")).intValue() : 10;
            int delayMs = rates != null ? ((Number) rates.get("delayMs")).intValue() : 200;
            
            return new ServiceStatus(running != null && running, infoRate, warnRate, errorRate, delayMs);
        } catch (Exception e) {
            return new ServiceStatus(false, 70, 20, 10, 200);
        }
    }
    
    public void startOrderService() {
        restTemplate.postForObject(orderServiceUrl + "/api/simulator/start", null, Void.class);
    }
    
    public void stopOrderService() {
        restTemplate.postForObject(orderServiceUrl + "/api/simulator/stop", null, Void.class);
    }
    
    public void updateOrderServiceRates(int infoRate, int warnRate, int errorRate, int delayMs) {
        String url = orderServiceUrl + "/api/simulator/rates?infoRate=" + infoRate + 
                    "&warnRate=" + warnRate + 
                    "&errorRate=" + errorRate;
        restTemplate.postForObject(url, null, Void.class);

        String delayUrl = orderServiceUrl + "/api/simulator/delay?delayMs=" + delayMs;
        restTemplate.postForObject(delayUrl, null, Void.class);
    }
    
    @Data
    public static class ServiceStatus {
        private final boolean running;
        private final int infoRate;
        private final int warnRate;
        private final int errorRate;
        private final int delayMs;
    }
} 