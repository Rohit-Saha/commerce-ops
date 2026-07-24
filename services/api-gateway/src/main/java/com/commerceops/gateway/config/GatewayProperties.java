package com.commerceops.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code commerce.gateway.*} configuration: downstream service base URLs, the
 * admin / storefront API keys, and the write-endpoint rate limit.
 */
@ConfigurationProperties(prefix = "commerce.gateway")
public record GatewayProperties(Services services, Admin admin, Storefront storefront, RateLimit rateLimit) {

    public record Services(
            String order,
            String inventory,
            String payment,
            String shipping,
            String saga,
            String customer,
            String catalog,
            String invoice
    ) {
    }

    public record Admin(String apiKey, String username, String password) {
    }

    public record Storefront(String apiKey) {
    }

    public record RateLimit(int limit, int windowSeconds) {
    }
}
