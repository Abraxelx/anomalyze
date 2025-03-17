package com.halilsahin.alertservice.controller;

import com.halilsahin.alertservice.model.Alert;
import com.halilsahin.alertservice.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {
    private final AlertService alertService;

    @PostMapping
    public ResponseEntity<Alert> createAlert(@RequestBody Alert alert) {
        Alert createdAlert = alertService.createAlert(alert);
        return ResponseEntity.ok(createdAlert);
    }
    
    @GetMapping("/all")
    public ResponseEntity<List<Alert>> getAllAlerts() {
        List<Alert> alerts = alertService.getAllAlerts();
        return ResponseEntity.ok(alerts);
    }
} 