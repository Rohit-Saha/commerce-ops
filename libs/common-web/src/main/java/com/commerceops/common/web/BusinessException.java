package com.commerceops.common.web;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Domain/business failure with a customer-facing {@code message}.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String error;
    private final List<String> details;

    public BusinessException(HttpStatus status, String error, String message) {
        this(status, error, message, null);
    }

    public BusinessException(HttpStatus status, String error, String message, List<String> details) {
        super(message);
        this.status = status;
        this.error = error;
        this.details = details;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "Bad Request", message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "Unauthorized", message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, "Forbidden", message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, "Not Found", message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, "Conflict", message);
    }

    public static BusinessException serviceUnavailable(String message) {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public List<String> getDetails() {
        return details;
    }
}
