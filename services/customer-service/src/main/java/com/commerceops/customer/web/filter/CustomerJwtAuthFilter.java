package com.commerceops.customer.web.filter;

import com.commerceops.customer.security.JwtService;
import com.commerceops.common.web.ApiError;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class CustomerJwtAuthFilter extends OncePerRequestFilter {

    public static final String CUSTOMER_ID_ATTR = "commerce.customerId";
    public static final String GATEWAY_CUSTOMER_HEADER = "X-Commerce-Customer-Id";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public CustomerJwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        return path.equals("/api/customers/register")
                || path.equals("/api/customers/login")
                || !path.startsWith("/api/customers/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Trusted when traffic comes from api-gateway (ClusterIP / private network).
        String gatewayCustomer = request.getHeader(GATEWAY_CUSTOMER_HEADER);
        if (gatewayCustomer != null && !gatewayCustomer.isBlank()) {
            request.setAttribute(CUSTOMER_ID_ATTR, gatewayCustomer.trim());
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response, "Missing Bearer token");
            return;
        }
        String token = header.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, "Missing Bearer token");
            return;
        }
        try {
            String customerId = jwtService.parse(token).getSubject();
            if (customerId == null || customerId.isBlank()) {
                writeUnauthorized(response, "Invalid token");
                return;
            }
            request.setAttribute(CUSTOMER_ID_ATTR, customerId);
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            writeUnauthorized(response, "Invalid or expired token");
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ApiError.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", message));
    }
}
