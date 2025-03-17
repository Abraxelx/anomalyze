package com.halilsahin.dashboard.controller;

import com.halilsahin.dashboard.model.Alert;
import com.halilsahin.dashboard.service.AlertService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public String getAlerts(Model model) {
        List<Alert> alerts = alertService.getAllAlerts();
        
        // İstatistikleri hesapla
        long highCount = alerts.stream().filter(a -> "HIGH".equals(a.getSeverity())).count();
        long mediumCount = alerts.stream().filter(a -> "MEDIUM".equals(a.getSeverity())).count();
        long lowCount = alerts.stream().filter(a -> "LOW".equals(a.getSeverity())).count();
        
        model.addAttribute("alerts", alerts);
        model.addAttribute("highCount", highCount);
        model.addAttribute("mediumCount", mediumCount);
        model.addAttribute("lowCount", lowCount);
        
        return "alerts";
    }
} 