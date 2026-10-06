package com.pixelbook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml içindeki pixelbook satırını taşır.
 * Gelen yaml: hmac-secret pixelbook-demo-hmac-anahtari.
 * İçeride alan aynı metinle durur.
 * Dışarı: hmacSecret() "pixelbook-demo-hmac-anahtari".
 */
@ConfigurationProperties(prefix = "pixelbook")
public record PixelbookProperties(String hmacSecret) {
}
