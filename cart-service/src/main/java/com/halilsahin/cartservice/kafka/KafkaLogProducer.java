package com.halilsahin.cartservice.kafka;

import com.halilsahin.cartservice.model.LogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaLogProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${spring.kafka.topic.logs:logs-topic}")
    private String logsTopic;

    /**
     * LogEvent'i Kafka'ya gönderir
     * @param logEvent gönderilecek log olayı
     */
    public void sendLogEvent(LogEvent logEvent) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                logsTopic, 
                logEvent.getRelatedObjectId(),
                logEvent
        );
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Log gönderildi: {}, offset: {}", 
                        logEvent.getRelatedObjectId(), 
                        result.getRecordMetadata().offset());
            } else {
                log.error("Log gönderimi başarısız oldu: {}", logEvent.getRelatedObjectId(), ex);
            }
        });
    }
} 