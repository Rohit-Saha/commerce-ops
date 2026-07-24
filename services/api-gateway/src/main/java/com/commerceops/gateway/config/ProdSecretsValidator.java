package com.commerceops.gateway.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fail closed when running under {@code k8s} or {@code prod}: refuse placeholder secrets.
 */
@Component
public class ProdSecretsValidator {

    private static final Logger log = LoggerFactory.getLogger(ProdSecretsValidator.class);

    private final Environment environment;
    private final SecurityProperties securityProperties;
    private final GatewayProperties gatewayProperties;
    private final CustomerJwtProperties customerJwtProperties;

    public ProdSecretsValidator(
            Environment environment,
            SecurityProperties securityProperties,
            GatewayProperties gatewayProperties,
            CustomerJwtProperties customerJwtProperties) {
        this.environment = environment;
        this.securityProperties = securityProperties;
        this.gatewayProperties = gatewayProperties;
        this.customerJwtProperties = customerJwtProperties;
    }

    @PostConstruct
    public void validate() {
        if (!isStrictProfile()) {
            return;
        }
        List<String> problems = new ArrayList<>();
        if (securityProperties.oidc()) {
            String issuer = environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
            if (!StringUtils.hasText(issuer)) {
                problems.add("spring.security.oauth2.resourceserver.jwt.issuer-uri must be set in oidc mode");
            }
            if (isPlaceholder(
                    environment.getProperty("spring.security.oauth2.client.registration.admin-ui-bff.client-secret"),
                    "admin-ui-bff-secret")) {
                problems.add("KEYCLOAK_ADMIN_UI_BFF_SECRET / admin-ui-bff client-secret must not use the default value");
            }
            if (isPlaceholder(
                    environment.getProperty("spring.security.oauth2.client.registration.storefront-bff.client-secret"),
                    "storefront-bff-secret")) {
                problems.add("KEYCLOAK_STOREFRONT_BFF_SECRET / storefront-bff client-secret must not use the default value");
            }
        } else {
            if (isPlaceholder(gatewayProperties.admin().apiKey(), "dev-admin-key")) {
                problems.add("commerce.gateway.admin.api-key must not use the default value");
            }
            if (isPlaceholder(gatewayProperties.admin().password(), "admin")) {
                problems.add("commerce.gateway.admin.password must not use the default value");
            }
            if (isPlaceholder(gatewayProperties.storefront().apiKey(), "storefront-key")) {
                problems.add("commerce.gateway.storefront.api-key must not use the default value");
            }
            if (isPlaceholder(customerJwtProperties.jwtSecret(), "commerce-ops-customer-jwt-dev-secret-change-me")) {
                problems.add("commerce.customer.jwt-secret must not use the default value");
            }
        }
        String webhook = environment.getProperty("commerce.security.require-webhook-secrets");
        if ("true".equalsIgnoreCase(webhook)) {
            // Enforced in payment/shipping services; gateway only logs expectation.
            log.info("Webhook secret enforcement is expected on payment/shipping (commerce.security.require-webhook-secrets=true)");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start under k8s/prod with insecure defaults: " + String.join("; ", problems));
        }
        log.info("Strict profile secret checks passed (mode={})", securityProperties.mode());
    }

    private boolean isStrictProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "k8s".equalsIgnoreCase(p) || "prod".equalsIgnoreCase(p));
    }

    private static boolean isPlaceholder(String value, String placeholder) {
        return !StringUtils.hasText(value) || placeholder.equals(value);
    }
}
