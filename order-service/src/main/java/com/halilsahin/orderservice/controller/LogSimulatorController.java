package com.halilsahin.orderservice.controller;

import com.halilsahin.orderservice.service.LogSimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class LogSimulatorController {
    private final LogSimulatorService logSimulatorService;
    
    @PostMapping("/start")
    public void start() {
        logSimulatorService.start();
    }
    
    @PostMapping("/stop")
    public void stop() {
        logSimulatorService.stop();
    }
    
    @PostMapping("/rates")
    public void updateRates(@RequestParam int infoRate, 
                          @RequestParam int warnRate, 
                          @RequestParam int errorRate) {
        logSimulatorService.updateRates(infoRate, warnRate, errorRate);
    }

    @GetMapping("/rates")
    public Map<String, Object> getRates() {
        return logSimulatorService.getRates();
    }
    
    @GetMapping("/status")
    public boolean isRunning() {
        return logSimulatorService.isRunning();
    }

    @PostMapping("/delay")
    public void updateDelay(@RequestParam int delayMs) {
        logSimulatorService.updateDelay(delayMs);
    }
} 