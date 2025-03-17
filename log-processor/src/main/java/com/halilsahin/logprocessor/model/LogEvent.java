package com.halilsahin.logprocessor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogEvent implements Serializable {
    private String id;
    private String serviceName;
    private String level;
    private LocalDateTime timestamp;
    private String message;
    private String relatedObjectId;
    private String eventType;
    private String contextData;
} 