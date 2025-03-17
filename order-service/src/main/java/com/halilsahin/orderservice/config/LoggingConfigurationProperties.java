package com.halilsahin.orderservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "logging.simulator")
public class LoggingConfigurationProperties {
    private boolean enabled = true;
} 