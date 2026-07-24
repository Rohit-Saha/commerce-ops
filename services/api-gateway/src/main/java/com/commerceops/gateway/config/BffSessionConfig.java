package com.commerceops.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@ConditionalOnProperty(prefix = "commerce.security", name = "mode", havingValue = "oidc")
@EnableConfigurationProperties(BffProperties.class)
public class BffSessionConfig {

    public static final String SESSION_COOKIE = "COMMERCE_SESSION";

    @Bean
    CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(SESSION_COOKIE);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        // Local HTTP; terminate TLS at Ingress and set Secure via reverse-proxy / SERVER_SERVLET_SESSION_COOKIE_SECURE.
        serializer.setUseSecureCookie(false);
        return serializer;
    }
}
