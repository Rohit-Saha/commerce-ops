package com.commerceops.gateway.service;

public record RateLimitResult(boolean allowed, int limit, long remaining, long retryAfterSeconds) {

    public static RateLimitResult allowed(int limit, long remaining) {
        return new RateLimitResult(true, limit, remaining, 0);
    }

    public static RateLimitResult rejected(int limit, long retryAfterSeconds) {
        return new RateLimitResult(false, limit, 0, retryAfterSeconds);
    }
}
