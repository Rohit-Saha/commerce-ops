package com.commerceops.gateway.web.filter;

import com.commerceops.common.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Requires a valid {@code X-API-Key} (or {@code ?apiKey=} for SSE) on every {@code /api/**}
 * request. Accepts the admin key or the storefront key; the role is stashed as a request
 * attribute for {@link StorefrontScopeFilter}.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String API_KEY_QUERY_PARAM = "apiKey";
    public static final String API_KEY_ROLE_ATTR = "commerce.apiKeyRole";

    public enum ApiKeyRole {
        ADMIN,
        STOREFRONT
    }

    private final String adminApiKey;
    private final String storefrontApiKey;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(String adminApiKey, String storefrontApiKey, ObjectMapper objectMapper) {
        this.adminApiKey = adminApiKey;
        this.storefrontApiKey = storefrontApiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path != null && (path.equals("/api/auth/login")
                || path.equals("/api/payments/webhooks/razorpay")
                || path.equals("/api/shipments/webhooks/shiprocket"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = resolveApiKey(request);
        ApiKeyRole role = resolveRole(provided);
        if (role == null) {
            writeUnauthorized(response);
            return;
        }
        request.setAttribute(API_KEY_ROLE_ATTR, role);
        chain.doFilter(request, response);
    }

    private ApiKeyRole resolveRole(String provided) {
        if (provided == null || provided.isBlank()) {
            return null;
        }
        if (adminApiKey != null && !adminApiKey.isBlank() && Objects.equals(adminApiKey, provided)) {
            return ApiKeyRole.ADMIN;
        }
        if (storefrontApiKey != null && !storefrontApiKey.isBlank() && Objects.equals(storefrontApiKey, provided)) {
            return ApiKeyRole.STOREFRONT;
        }
        return null;
    }

    private static String resolveApiKey(HttpServletRequest request) {
        String header = request.getHeader(API_KEY_HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        String query = request.getParameter(API_KEY_QUERY_PARAM);
        if (query != null && !query.isBlank()) {
            return query;
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized",
                "Please provide a valid API key to continue.");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
