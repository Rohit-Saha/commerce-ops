package com.commerceops.gateway.web.filter;

import com.commerceops.gateway.service.RateLimitResult;
import com.commerceops.gateway.service.RateLimiterService;
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
import java.util.Set;

/**
 * Applies the Redis sliding-window rate limit to write methods (POST/PUT/PATCH/DELETE)
 * under {@code /api/**}. Read-only GET traffic is never throttled.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null
                && (path.startsWith("/api/auth/")
                        || path.equals("/api/payments/webhooks/razorpay")
                        || path.equals("/api/shipments/webhooks/shiprocket"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String identifier = resolveIdentifier(request);
        RateLimitResult result = rateLimiterService.tryAcquire(identifier);

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        if (result.allowed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
            chain.doFilter(request, response);
            return;
        }

        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                "Too many requests. Please wait a moment and try again.",
                null,
                request.getRequestURI(),
                (int) result.retryAfterSeconds());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private String resolveIdentifier(HttpServletRequest request) {
        String apiKey = request.getHeader(ApiKeyAuthFilter.API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return "key:" + apiKey;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
