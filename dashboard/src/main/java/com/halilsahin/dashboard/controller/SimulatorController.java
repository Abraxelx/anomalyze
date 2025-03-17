package com.halilsahin.dashboard.controller;

import com.halilsahin.dashboard.service.SimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class SimulatorController {
    
    private final SimulatorService simulatorService;
    
    @GetMapping("/")
    public String home(Model model) {
        SimulatorService.ServiceStatus status = simulatorService.getOrderServiceStatus();
        model.addAttribute("status", status);
        return "home";
    }
    
    @PostMapping("/start")
    public String startOrderService() {
        simulatorService.startOrderService();
        return "redirect:/";
    }
    
    @PostMapping("/stop")
    public String stopOrderService() {
        simulatorService.stopOrderService();
        return "redirect:/";
    }
    
    @PostMapping("/update-rates")
    public String updateRates(@RequestParam int infoRate, 
                            @RequestParam int warnRate,
                            @RequestParam int errorRate,
                            @RequestParam int delayMs) {
        simulatorService.updateOrderServiceRates(infoRate, warnRate, errorRate, delayMs);
        return "redirect:/";
    }
} 