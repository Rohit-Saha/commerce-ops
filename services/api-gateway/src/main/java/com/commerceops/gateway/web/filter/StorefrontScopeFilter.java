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

/**
 * Restricts the storefront API key to catalog, auth, addresses, and customer orders.
 * Admin key is unrestricted (subject to the other filters).
 */
public class StorefrontScopeFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public StorefrontScopeFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Object role = request.getAttribute(ApiKeyAuthFilter.API_KEY_ROLE_ATTR);
        return role != ApiKeyAuthFilter.ApiKeyRole.STOREFRONT;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isAllowed(request.getMethod(), request.getRequestURI())) {
            writeForbidden(response);
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean isAllowed(String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        String m = method.toUpperCase();

        if ("GET".equals(m) && "/api/store/catalog".equals(path)) {
            return true;
        }
        if ("GET".equals(m) && "/api/store/catalog/search".equals(path)) {
            return true;
        }
        if ("GET".equals(m) && "/api/store/catalog/categories".equals(path)) {
            return true;
        }
        if ("GET".equals(m) && path.startsWith("/api/store/catalog/by-slug/")
                && path.length() > "/api/store/catalog/by-slug/".length()) {
            return !path.substring("/api/store/catalog/by-slug/".length()).contains("/");
        }
        if ("GET".equals(m) && path.startsWith("/api/store/catalog/") && path.length() > "/api/store/catalog/".length()) {
            String rest = path.substring("/api/store/catalog/".length());
            return !rest.contains("/") && !rest.isBlank()
                    && !"search".equals(rest)
                    && !"categories".equals(rest);
        }
        if ("GET".equals(m) && "/api/inventory".equals(path)) {
            return true;
        }
        if ("GET".equals(m) && path.startsWith("/api/inventory/") && path.length() > "/api/inventory/".length()) {
            String rest = path.substring("/api/inventory/".length());
            return !rest.contains("/") && !rest.isBlank();
        }

        if ("POST".equals(m) && "/api/customers/register".equals(path)) {
            return true;
        }
        if ("POST".equals(m) && "/api/customers/login".equals(path)) {
            return true;
        }
        if (path.startsWith("/api/customers/me")) {
            return "GET".equals(m) || "POST".equals(m) || "PUT".equals(m) || "DELETE".equals(m);
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
            return !rest.contains("/") && !rest.isBlank();
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

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(
                HttpStatus.FORBIDDEN.value(), "Forbidden",
                "You don’t have permission to access this endpoint.");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
