package com.commerceops.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        boolean success,
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details,
        String path,
        Integer retryAfterSeconds
) {

    public static ApiError of(int status, String error, String message) {
        return of(status, error, message, null, null, null);
    }

    public static ApiError of(int status, String error, String message, List<String> details) {
        return of(status, error, message, details, null, null);
    }

    public static ApiError of(
            int status,
            String error,
            String message,
            List<String> details,
            String path,
            Integer retryAfterSeconds
    ) {
        return new ApiError(
                false,
                Instant.now(),
                status,
                error,
                message,
                details == null || details.isEmpty() ? null : List.copyOf(details),
                path,
                retryAfterSeconds
        );
    }

    public ApiError withPath(String path) {
        return new ApiError(success, timestamp, status, error, message, details, path, retryAfterSeconds);
    }

    public ApiError withRetryAfterSeconds(Integer retryAfterSeconds) {
        return new ApiError(success, timestamp, status, error, message, details, path, retryAfterSeconds);
    }
}
