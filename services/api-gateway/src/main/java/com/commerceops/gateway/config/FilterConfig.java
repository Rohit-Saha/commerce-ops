package com.commerceops.gateway.config;

import com.commerceops.gateway.security.CustomerJwtService;
import com.commerceops.gateway.security.OidcIdentityBridgeFilter;
import com.commerceops.gateway.security.SecurityHeadersFilter;
import com.commerceops.gateway.service.RateLimiterService;
import com.commerceops.gateway.web.filter.ApiKeyAuthFilter;
import com.commerceops.gateway.web.filter.CustomerJwtFilter;
import com.commerceops.gateway.web.filter.RateLimitFilter;
import com.commerceops.gateway.web.filter.StorefrontScopeFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers auth and rate-limit filters. Legacy API-key filters are only active when
 * {@code commerce.security.mode=legacy} (default). OIDC mode uses Spring Security instead.
 */
@Configuration
public class FilterConfig {

    @Bean
    @ConditionalOnProperty(prefix = "commerce.security", name = "mode", havingValue = "legacy", matchIfMissing = true)
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(
            GatewayProperties properties, ObjectMapper objectMapper) {
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>(
                new ApiKeyAuthFilter(
                        properties.admin().apiKey(),
                        properties.storefront().apiKey(),
                        objectMapper));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        registration.setName("apiKeyAuthFilter");
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "commerce.security", name = "mode", havingValue = "legacy", matchIfMissing = true)
    public FilterRegistrationBean<StorefrontScopeFilter> storefrontScopeFilter(ObjectMapper objectMapper) {
        FilterRegistrationBean<StorefrontScopeFilter> registration = new FilterRegistrationBean<>(
                new StorefrontScopeFilter(objectMapper));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);
        registration.setName("storefrontScopeFilter");
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "commerce.security", name = "mode", havingValue = "legacy", matchIfMissing = true)
    public FilterRegistrationBean<CustomerJwtFilter> customerJwtFilter(
            CustomerJwtService jwtService, ObjectMapper objectMapper) {
        FilterRegistrationBean<CustomerJwtFilter> registration = new FilterRegistrationBean<>(
                new CustomerJwtFilter(jwtService, objectMapper));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(3);
        registration.setName("customerJwtFilter");
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "commerce.security", name = "mode", havingValue = "oidc")
    public FilterRegistrationBean<OidcIdentityBridgeFilter> oidcIdentityBridgeFilter() {
        FilterRegistrationBean<OidcIdentityBridgeFilter> registration =
                new FilterRegistrationBean<>(new OidcIdentityBridgeFilter());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 20);
        registration.setName("oidcIdentityBridgeFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> registration =
                new FilterRegistrationBean<>(new SecurityHeadersFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("securityHeadersFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
                new RateLimitFilter(rateLimiterService, objectMapper));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(4);
        registration.setName("rateLimitFilter");
        return registration;
    }
}
