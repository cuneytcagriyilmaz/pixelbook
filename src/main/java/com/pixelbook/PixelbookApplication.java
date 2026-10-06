package com.pixelbook;

import com.pixelbook.config.PixelbookProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Uygulamayı ayağa kaldırır.
 * Gelen: args boş dizi olabilir.
 * İçeride: Spring, 8080 portunu ve H2 bellek veritabanını açar.
 * Dışarı: tarayıcı http://localhost:8080 adresinde dört ekranı görür.
 */
@SpringBootApplication
@EnableConfigurationProperties(PixelbookProperties.class)
public class PixelbookApplication {

    /**
     * Süreç girişi.
     * Gelen: komut satırı argümanları, demo çalıştırmada boş.
     * İçeride: SpringApplication.run bu sınıfı tarar.
     * Dışarı: çalışan sunucu. Bu metot sunucu inene kadar geri dönmez.
     */
    public static void main(String[] args) {
        SpringApplication.run(PixelbookApplication.class, args);
    }
}
