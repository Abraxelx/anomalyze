package com.halilsahin.anomalydetector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AnomalyDetectorApplication {
    public static void main(String[] args) {
        System.setProperty("illegal-access", "permit");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.security.manager", "allow");
        
        String argOptions = ""
            + "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED "
            + "--add-opens=java.base/java.nio=ALL-UNNAMED "
            + "--add-opens=java.base/java.lang=ALL-UNNAMED "
            + "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED "
            + "--add-opens=java.base/java.util=ALL-UNNAMED "
            + "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED "
            + "--add-opens=java.base/sun.net.dns=ALL-UNNAMED "
            + "--add-opens=java.base/sun.net.util=ALL-UNNAMED "
            + "--add-opens=java.management/sun.management=ALL-UNNAMED "
            + "--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED "
            + "--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED";
        
        SpringApplication app = new SpringApplication(AnomalyDetectorApplication.class);
        app.setDefaultProperties(java.util.Collections.singletonMap("spring.jvm.options", argOptions));
        app.run(args);
    }
} 