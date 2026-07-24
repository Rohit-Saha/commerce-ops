package com.commerceops.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "commerce.customer")
public record CustomerJwtProperties(String jwtSecret, int jwtTtlHours) {
}
