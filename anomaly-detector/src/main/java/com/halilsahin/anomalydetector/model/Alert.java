package com.halilsahin.anomalydetector.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class Alert {
    private String type;
    private String severity;
    private String source;
    private LocalDateTime timestamp;
    private String description;
    private Map<String, Object> metadata;
}