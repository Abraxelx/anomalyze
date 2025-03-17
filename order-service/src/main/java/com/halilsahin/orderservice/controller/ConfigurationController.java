package com.halilsahin.orderservice.controller;

import com.halilsahin.orderservice.config.LoggingConfigurationProperties;
import com.halilsahin.orderservice.model.LogFrequencyRequest;
import com.halilsahin.orderservice.model.LogLevelRatioRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigurationController {

    private final LoggingConfigurationProperties loggingConfig;

    @GetMapping("/logging")
    public ResponseEntity<LoggingConfigurationProperties> getLoggingConfiguration() {
        return ResponseEntity.ok(loggingConfig);
    }

    @PutMapping("/logging/levels")
    public ResponseEntity<?> updateLogLevels(@RequestBody LogLevelRatioRequest request) {
        int total = request.getErrorRate() + request.getWarnRate() + request.getInfoRate();
        if (total != 100) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Log seviye oranları toplamı 100 olmalıdır")
            );
        }
        
        log.info("Log seviye oranları güncellendi: ERROR={}, WARN={}, INFO={}", 
                request.getErrorRate(), request.getWarnRate(), request.getInfoRate());

        return ResponseEntity.ok(loggingConfig);
    }

    @PutMapping("/logging/frequency")
    public ResponseEntity<?> updateLogFrequency(@RequestBody LogFrequencyRequest request) {
        if (request.getLogsPerSecond() <= 0) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Saniyedeki log sayısı pozitif olmalıdır")
            );
        }

        log.info("Log üretim frekansı güncellendi: {} log/saniye", request.getLogsPerSecond());

        return ResponseEntity.ok(loggingConfig);
    }

    @PutMapping("/logging/toggle")
    public ResponseEntity<?> toggleLogging(@RequestBody Map<String, Boolean> request) {
        Boolean enabled = request.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "enabled parametresi gereklidir")
            );
        }

        loggingConfig.setEnabled(enabled);
        log.info("Log üretimi {} durumuna getirildi", enabled ? "aktif" : "devre dışı");

        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    /**
     * Servis durumunu döndürür
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "order-service");
        status.put("loggingEnabled", loggingConfig.isEnabled());
        status.put("loggingConfig", loggingConfig);
        
        return ResponseEntity.ok(status);
    }
} 