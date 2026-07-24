package com.commerceops.gateway.config;

import com.commerceops.gateway.security.AuthSessionTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@ConditionalOnProperty(prefix = "commerce.security", name = "mode", havingValue = "oidc")
public class OidcSecurityConfig {

    public static final String SSE_COOKIE = "commerce_sse_token";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                // BFF needs an HTTP session (Redis) for the opaque session cookie.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/payments/webhooks/**",
                                "/api/shipments/webhooks/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/store/catalog",
                                "/api/store/catalog/**",
                                "/api/inventory",
                                "/api/inventory/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/login", "/api/auth/callback/**", "/api/auth/logout")
                                .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/sagas/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/shipments").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/shipments/*/advance").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/shipments/chaos").hasRole("ADMIN")
                        .requestMatchers("/api/stream/**").hasAnyRole("ADMIN", "CUSTOMER")
                        .requestMatchers(
                                "/api/orders/**",
                                "/api/payments/**",
                                "/api/shipments/**",
                                "/api/invoices/**",
                                "/api/customers/**",
                                "/api/auth/**").hasAnyRole("ADMIN", "CUSTOMER")
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Prefer Authorization header, then BFF session access token, then SSE cookie / query fallback.
     */
    @Bean
    BearerTokenResolver bearerTokenResolver() {
        return request -> {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring("Bearer ".length()).trim();
                if (!token.isEmpty()) {
                    return token;
                }
            }
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object attr = session.getAttribute(AuthSessionTokens.SESSION_ATTR);
                if (attr instanceof AuthSessionTokens tokens && tokens.getAccessToken() != null
                        && !tokens.getAccessToken().isBlank()) {
                    return tokens.getAccessToken();
                }
            }
            String cookie = cookieValue(request, SSE_COOKIE);
            if (cookie != null && !cookie.isBlank()) {
                return cookie;
            }
            String query = request.getParameter("access_token");
            if (query != null && !query.isBlank()) {
                return query;
            }
            return null;
        };
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(OidcSecurityConfig::rolesFromJwt);
        return converter;
    }

    static Collection<GrantedAuthority> rolesFromJwt(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            Object roles = map.get("roles");
            if (roles instanceof Collection<?> collection) {
                for (Object role : collection) {
                    if (role == null) {
                        continue;
                    }
                    String name = role.toString();
                    if ("admin".equalsIgnoreCase(name)) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    } else if ("customer".equalsIgnoreCase(name)) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
                    }
                }
            }
        }
        return authorities;
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
