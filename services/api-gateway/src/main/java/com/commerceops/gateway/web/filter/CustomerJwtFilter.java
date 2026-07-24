package com.commerceops.gateway.web.filter;

import com.commerceops.gateway.security.CustomerJwtService;
import com.commerceops.common.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Requires a customer JWT on storefront-protected paths and stashes {@code customerId}.
 */
public class CustomerJwtFilter extends OncePerRequestFilter {

    public static final String CUSTOMER_ID_ATTR = "commerce.customerId";
    public static final String CUSTOMER_TOKEN_ATTR = "commerce.customerToken";

    private final CustomerJwtService jwtService;
    private final ObjectMapper objectMapper;

    public CustomerJwtFilter(CustomerJwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Object role = request.getAttribute(ApiKeyAuthFilter.API_KEY_ROLE_ATTR);
        if (role != ApiKeyAuthFilter.ApiKeyRole.STOREFRONT) {
            return true;
        }
        return !requiresCustomerJwt(request.getMethod(), request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response, "Login required");
            return;
        }
        String token = header.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, "Login required");
            return;
        }
        try {
            String customerId = jwtService.parse(token).getSubject();
            if (customerId == null || customerId.isBlank()) {
                writeUnauthorized(response, "Invalid token");
                return;
            }
            request.setAttribute(CUSTOMER_ID_ATTR, customerId);
            request.setAttribute(CUSTOMER_TOKEN_ATTR, token);
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            writeUnauthorized(response, "Invalid or expired token");
        }
    }

    static boolean requiresCustomerJwt(String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        String m = method.toUpperCase();
        if (path.startsWith("/api/customers/me")) {
            return true;
        }
        if ("POST".equals(m) && "/api/orders".equals(path)) {
            return true;
        }
        if ("POST".equals(m) && "/api/payments/razorpay/orders".equals(path)) {
            return true;
        }
        if ("GET".equals(m) && "/api/orders/mine".equals(path)) {
            return true;
        }
        if ("GET".equals(m) && path.startsWith("/api/orders/") && path.length() > "/api/orders/".length()) {
            String rest = path.substring("/api/orders/".length());
            return !rest.contains("/") && !rest.isBlank() && !"mine".equals(rest);
        }
        if ("POST".equals(m) && path.startsWith("/api/orders/") && path.endsWith("/cancel")) {
            String mid = path.substring("/api/orders/".length(), path.length() - "/cancel".length());
            return !mid.isBlank() && !mid.contains("/");
        }
        if ("GET".equals(m) && path.startsWith("/api/invoices/by-order/")
                && path.length() > "/api/invoices/by-order/".length()) {
            String rest = path.substring("/api/invoices/by-order/".length());
            return !rest.contains("/") && !rest.isBlank();
        }
        if ("GET".equals(m) && path.startsWith("/api/invoices/") && path.length() > "/api/invoices/".length()) {
            String rest = path.substring("/api/invoices/".length());
            if (rest.endsWith("/pdf")) {
                String id = rest.substring(0, rest.length() - "/pdf".length());
                return !id.isBlank() && !id.contains("/");
            }
            return !rest.contains("/") && !rest.isBlank() && !"by-order".equals(rest);
        }
        if ("GET".equals(m) && path.startsWith("/api/shipments/by-order/")
                && path.length() > "/api/shipments/by-order/".length()) {
            String rest = path.substring("/api/shipments/by-order/".length());
            return !rest.contains("/") && !rest.isBlank();
        }
        if ("GET".equals(m) && path.startsWith("/api/shipments/") && path.length() > "/api/shipments/".length()) {
            String rest = path.substring("/api/shipments/".length());
            if (rest.endsWith("/events")) {
                String id = rest.substring(0, rest.length() - "/events".length());
                return !id.isBlank() && !id.contains("/");
            }
            return !rest.contains("/") && !rest.isBlank()
                    && !"by-order".equals(rest)
                    && !"chaos".equals(rest)
                    && !"webhooks".equals(rest);
        }
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ApiError.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", message));
    }
}
