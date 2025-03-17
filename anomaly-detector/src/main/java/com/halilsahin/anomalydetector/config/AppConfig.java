package com.halilsahin.anomalydetector.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        System.out.println("RestTemplate yapılandırıldı: 5 saniye timeout ile");
        
        return restTemplate;
    }
} 