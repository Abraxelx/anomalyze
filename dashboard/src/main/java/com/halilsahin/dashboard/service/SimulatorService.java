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

    @Value("${service.cart.url}")
    private String cartServiceUrl;

    @Value("${service.product.url}")
    private String productServiceUrl;
    
    public ServiceStatus getOrderServiceStatus() {
        return getServiceStatus(orderServiceUrl);
    }

    public ServiceStatus getCartServiceStatus() {
        return getServiceStatus(cartServiceUrl);
    }

    public ServiceStatus getProductServiceStatus() {
        return getServiceStatus(productServiceUrl);
    }

    private ServiceStatus getServiceStatus(String serviceUrl) {
        try {
            Boolean running = restTemplate.getForObject(serviceUrl + "/api/simulator/status", Boolean.class);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                serviceUrl + "/api/simulator/rates",
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
        startService(orderServiceUrl);
    }

    public void startCartService() {
        startService(cartServiceUrl);
    }

    public void startProductService() {
        startService(productServiceUrl);
    }

    private void startService(String serviceUrl) {
        restTemplate.postForObject(serviceUrl + "/api/simulator/start", null, Void.class);
    }
    
    public void stopOrderService() {
        stopService(orderServiceUrl);
    }

    public void stopCartService() {
        stopService(cartServiceUrl);
    }

    public void stopProductService() {
        stopService(productServiceUrl);
    }

    private void stopService(String serviceUrl) {
        restTemplate.postForObject(serviceUrl + "/api/simulator/stop", null, Void.class);
    }
    
    public void updateOrderServiceRates(int infoRate, int warnRate, int errorRate, int delayMs) {
        updateServiceRates(orderServiceUrl, infoRate, warnRate, errorRate, delayMs);
    }

    public void updateCartServiceRates(int infoRate, int warnRate, int errorRate, int delayMs) {
        updateServiceRates(cartServiceUrl, infoRate, warnRate, errorRate, delayMs);
    }

    public void updateProductServiceRates(int infoRate, int warnRate, int errorRate, int delayMs) {
        updateServiceRates(productServiceUrl, infoRate, warnRate, errorRate, delayMs);
    }

    private void updateServiceRates(String serviceUrl, int infoRate, int warnRate, int errorRate, int delayMs) {
        String url = serviceUrl + "/api/simulator/rates?infoRate=" + infoRate + 
                    "&warnRate=" + warnRate + 
                    "&errorRate=" + errorRate;
        restTemplate.postForObject(url, null, Void.class);

        String delayUrl = serviceUrl + "/api/simulator/delay?delayMs=" + delayMs;
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