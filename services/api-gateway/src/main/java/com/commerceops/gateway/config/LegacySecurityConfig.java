package com.commerceops.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Legacy mode: disable Spring Security's default lock-down so existing servlet filters own auth.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(prefix = "commerce.security", name = "mode", havingValue = "legacy", matchIfMissing = true)
public class LegacySecurityConfig {

    @Bean
    SecurityFilterChain legacySecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
