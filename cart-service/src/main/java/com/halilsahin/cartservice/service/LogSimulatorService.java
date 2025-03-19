package com.halilsahin.cartservice.service;

import com.halilsahin.cartservice.kafka.KafkaLogProducer;
import com.halilsahin.cartservice.model.LogEvent;
import com.halilsahin.cartservice.model.LogLevel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogSimulatorService {
    private static final Random RANDOM = new Random();
    private final KafkaLogProducer kafkaLogProducer;
    private int cartCounter = 0;
    private boolean isRunning = false;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;

    @Value("${logging.simulator.fixed-delay-ms}")
    private int fixedDelayMs;

    @Value("${spring.application.name}")
    private String serviceName;

    @Value("${logging.simulator.info-rate}")
    private int infoRate;

    @Value("${logging.simulator.warn-rate}")
    private int warnRate;

    @Value("${logging.simulator.error-rate}")
    private int errorRate;
    
    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }
    
    @PreDestroy
    public void cleanup() {
        stop();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
    
    private void scheduleLogGeneration() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        
        if (isRunning) {
            scheduledTask = scheduler.scheduleWithFixedDelay(
                this::generateLogs,
                0,
                fixedDelayMs,
                TimeUnit.MILLISECONDS
            );
        }
    }
    
    private void generateLogs() {
        LogEvent logEvent = LogEvent.builder()
                .level(getRandomLogLevel())
                .message(String.format("Sepet işlemi: CART-%d", ++cartCounter))
                .relatedObjectId("CART-" + cartCounter)
                .serviceName(serviceName)
                .build();
        
        kafkaLogProducer.sendLogEvent(logEvent);
    }
    
    private LogLevel getRandomLogLevel() {
        int rand = RANDOM.nextInt(100);
        if (rand < infoRate) return LogLevel.INFO;
        if (rand < infoRate + warnRate) return LogLevel.WARN;
        return LogLevel.ERROR;
    }
    
    public void updateRates(int infoRate, int warnRate, int errorRate) {
        if (infoRate + warnRate + errorRate != 100) {
            throw new IllegalArgumentException("Toplam oran 100 olmalıdır");
        }
        this.infoRate = infoRate;
        this.warnRate = warnRate;
        this.errorRate = errorRate;
    }

    public void updateDelay(int delayMs) {
        if (delayMs < 1) {
            throw new IllegalArgumentException("Delay değeri 1'den küçük olamaz");
        }
        this.fixedDelayMs = delayMs;
        log.info("Log üretim gecikmesi {} ms olarak güncellendi", delayMs);
        scheduleLogGeneration();
    }

    public Map<String, Object> getRates() {
        Map<String, Object> rates = new HashMap<>();
        rates.put("infoRate", infoRate);
        rates.put("warnRate", warnRate);
        rates.put("errorRate", errorRate);
        rates.put("delayMs", fixedDelayMs);
        return rates;
    }
    
    public void start() {
        this.isRunning = true;
        log.info("Log üretimi başlatıldı");
        scheduleLogGeneration();
    }
    
    public void stop() {
        this.isRunning = false;
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
        log.info("Log üretimi durduruldu");
    }
    
    public boolean isRunning() {
        return isRunning;
    }
} 