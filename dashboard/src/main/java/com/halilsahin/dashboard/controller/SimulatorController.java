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
        SimulatorService.ServiceStatus orderStatus = simulatorService.getOrderServiceStatus();
        SimulatorService.ServiceStatus cartStatus = simulatorService.getCartServiceStatus();
        SimulatorService.ServiceStatus productStatus = simulatorService.getProductServiceStatus();
        model.addAttribute("orderStatus", orderStatus);
        model.addAttribute("cartStatus", cartStatus);
        model.addAttribute("productStatus", productStatus);
        return "home";
    }
    
    @PostMapping("/order/start")
    public String startOrderService() {
        simulatorService.startOrderService();
        return "redirect:/";
    }
    
    @PostMapping("/order/stop")
    public String stopOrderService() {
        simulatorService.stopOrderService();
        return "redirect:/";
    }
    
    @PostMapping("/order/update-rates")
    public String updateOrderRates(@RequestParam int infoRate, 
                                 @RequestParam int warnRate,
                                 @RequestParam int errorRate,
                                 @RequestParam int delayMs) {
        simulatorService.updateOrderServiceRates(infoRate, warnRate, errorRate, delayMs);
        return "redirect:/";
    }

    @PostMapping("/cart/start")
    public String startCartService() {
        simulatorService.startCartService();
        return "redirect:/";
    }
    
    @PostMapping("/cart/stop")
    public String stopCartService() {
        simulatorService.stopCartService();
        return "redirect:/";
    }
    
    @PostMapping("/cart/update-rates")
    public String updateCartRates(@RequestParam int infoRate, 
                                @RequestParam int warnRate,
                                @RequestParam int errorRate,
                                @RequestParam int delayMs) {
        simulatorService.updateCartServiceRates(infoRate, warnRate, errorRate, delayMs);
        return "redirect:/";
    }

    @PostMapping("/product/start")
    public String startProductService() {
        simulatorService.startProductService();
        return "redirect:/";
    }
    
    @PostMapping("/product/stop")
    public String stopProductService() {
        simulatorService.stopProductService();
        return "redirect:/";
    }
    
    @PostMapping("/product/update-rates")
    public String updateProductRates(@RequestParam int infoRate, 
                                   @RequestParam int warnRate,
                                   @RequestParam int errorRate,
                                   @RequestParam int delayMs) {
        simulatorService.updateProductServiceRates(infoRate, warnRate, errorRate, delayMs);
        return "redirect:/";
    }
} 