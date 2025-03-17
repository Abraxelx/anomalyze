package com.halilsahin.anomalydetector.config;

import org.apache.spark.SparkConf;
import org.apache.spark.sql.SparkSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

@Configuration
public class SparkConfig {

    static {
        // Tüm gerekli Java modül açılımlarını ayarla
        System.setProperty("illegal-access", "permit");
        
        List<String> modules = Arrays.asList(
            "java.base/sun.nio.ch",
            "java.base/java.nio", 
            "java.base/java.lang",
            "java.base/java.lang.invoke",
            "java.base/java.util",
            "java.base/java.util.concurrent",
            "java.base/sun.net.dns",
            "java.base/sun.net.util",
            "java.management/sun.management",
            "jdk.management/com.sun.management.internal",
            "jdk.unsupported/sun.misc"
        );
        
        for (String module : modules) {
            System.setProperty("--add-opens=" + module + "=ALL-UNNAMED", "true");
            System.setProperty("--add-exports=" + module + "=ALL-UNNAMED", "true");
        }
    }

    @Bean
    public SparkSession sparkSession() {
        // Kullanıcı ana dizininde geçici checkpoint dizini oluştur
        String userHome = System.getProperty("user.home");
        String checkpointDir = userHome + "/anomalyze-checkpoint";
        
        try {
            // Dizin yoksa oluştur
            Files.createDirectories(Paths.get(checkpointDir));
            System.out.println("Checkpoint dizini: " + checkpointDir);
        } catch (IOException e) {
            System.err.println("Checkpoint dizini oluşturulurken hata: " + e.getMessage());
            // Alternatif olarak geçici dizini kullan
            checkpointDir = System.getProperty("java.io.tmpdir") + "/anomalyze-checkpoint";
            System.out.println("Alternatif checkpoint dizini: " + checkpointDir);
        }
        
        SparkConf conf = new SparkConf()
            .setAppName("AnomalyDetector")
            .setMaster("local[*]")
            .set("spark.ui.enabled", "false")
            .set("spark.sql.streaming.checkpointLocation", checkpointDir)
            .set("spark.driver.host", "localhost")
            .set("spark.driver.extraJavaOptions", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED " +
                "--add-opens=java.base/java.nio=ALL-UNNAMED " +
                "--add-opens=java.base/java.lang=ALL-UNNAMED " +
                "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED " +
                "--add-opens=java.base/java.util=ALL-UNNAMED " +
                "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED " +
                "--add-opens=java.base/sun.net.dns=ALL-UNNAMED " +
                "--add-opens=java.base/sun.net.util=ALL-UNNAMED " +
                "--add-opens=java.management/sun.management=ALL-UNNAMED " +
                "--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED " +
                "--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED");

        return SparkSession.builder()
            .config(conf)
            .getOrCreate();
    }
} 