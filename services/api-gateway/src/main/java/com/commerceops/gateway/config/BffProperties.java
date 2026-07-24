package com.commerceops.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "commerce.bff")
public record BffProperties(
        String adminFrontendUrl,
        String storefrontFrontendUrl,
        long refreshSkewSeconds
) {
    public BffProperties {
        if (adminFrontendUrl == null || adminFrontendUrl.isBlank()) {
            adminFrontendUrl = "http://localhost:5173";
        }
        if (storefrontFrontendUrl == null || storefrontFrontendUrl.isBlank()) {
            storefrontFrontendUrl = "http://localhost:5174";
        }
        if (refreshSkewSeconds <= 0) {
            refreshSkewSeconds = 60;
        }
    }
}
