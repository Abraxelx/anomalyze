package com.halilsahin.alertservice.service;

import com.halilsahin.alertservice.model.Alert;
import com.halilsahin.alertservice.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {
    private final AlertRepository alertRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public Alert createAlert(Alert alert) {
        Alert savedAlert = alertRepository.save(alert);
        notifyClients(savedAlert);
        return savedAlert;
    }
    
    public List<Alert> getAllAlerts() {
        return alertRepository.findAll();
    }

    private void notifyClients(Alert alert) {
        messagingTemplate.convertAndSend("/topic/alerts", alert);
        log.info("Alert bildirimi gönderildi: {}", alert);
    }
} 