package com.commerceops.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "commerce.security")
public record SecurityProperties(
        /**
         * {@code legacy} = API keys + custom JWT filters (default for local demos).
         * {@code oidc} = Keycloak JWT via Spring Security OAuth2 resource server.
         */
        String mode
) {
    public boolean oidc() {
        return "oidc".equalsIgnoreCase(mode);
    }

    public boolean legacy() {
        return !oidc();
    }
}
