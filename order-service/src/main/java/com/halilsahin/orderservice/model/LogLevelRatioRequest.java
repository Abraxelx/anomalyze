package com.halilsahin.orderservice.model;

import lombok.Data;

@Data
public class LogLevelRatioRequest {
    private int errorRate;
    private int warnRate;
    private int infoRate;
} 