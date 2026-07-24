package com.commerceops.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "commerce.catalog")
public record CatalogProperties(
        String inventoryBaseUrl,
        String strapiBaseUrl,
        String strapiWebhookSecret,
        String elasticsearchUrl,
        String publicStrapiUrl
) {
}
