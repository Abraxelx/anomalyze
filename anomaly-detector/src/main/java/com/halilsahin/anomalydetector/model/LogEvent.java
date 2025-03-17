package com.halilsahin.anomalydetector.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LogEvent {
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;
    private String level;
    private String message;
    private String relatedObjectId;
    private String serviceName;
} 