package com.commerceops.gateway.security;

import com.commerceops.gateway.web.filter.ApiKeyAuthFilter;
import com.commerceops.gateway.web.filter.CustomerJwtFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bridges Spring Security JWT auth into the legacy request attributes used by proxy controllers
 * ({@code API_KEY_ROLE_ATTR}, {@code CUSTOMER_ID_ATTR}).
 */
public class OidcIdentityBridgeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            boolean admin = hasRole(jwtAuth, "ROLE_ADMIN");
            boolean customer = hasRole(jwtAuth, "ROLE_CUSTOMER");
            if (admin) {
                request.setAttribute(ApiKeyAuthFilter.API_KEY_ROLE_ATTR, ApiKeyAuthFilter.ApiKeyRole.ADMIN);
            } else if (customer) {
                request.setAttribute(ApiKeyAuthFilter.API_KEY_ROLE_ATTR, ApiKeyAuthFilter.ApiKeyRole.STOREFRONT);
            }
            Jwt jwt = jwtAuth.getToken();
            String customerId = firstNonBlank(
                    jwt.getClaimAsString("customer_id"),
                    jwt.getClaimAsString("customerId"),
                    jwt.getSubject());
            if (customerId != null) {
                request.setAttribute(CustomerJwtFilter.CUSTOMER_ID_ATTR, customerId);
            }
            request.setAttribute(CustomerJwtFilter.CUSTOMER_TOKEN_ATTR, jwt.getTokenValue());
            String email = jwt.getClaimAsString("email");
            if (email != null) {
                request.setAttribute("commerce.email", email);
            }
            String display = firstNonBlank(jwt.getClaimAsString("name"), jwt.getClaimAsString("preferred_username"));
            if (display != null) {
                request.setAttribute("commerce.displayName", display);
            }
            request.setAttribute("commerce.actor", jwt.getClaimAsString("preferred_username") != null
                    ? jwt.getClaimAsString("preferred_username")
                    : jwt.getSubject());
        }
        chain.doFilter(request, response);
    }

    private static boolean hasRole(Authentication authentication, String role) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (role.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
