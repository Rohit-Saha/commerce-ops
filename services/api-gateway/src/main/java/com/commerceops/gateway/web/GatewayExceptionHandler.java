package com.commerceops.gateway.web;

import com.commerceops.common.web.ApiError;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gateway-specific handling: passthrough downstream error bodies, and map unreachable peers.
 * Shared {@code ApiExceptionHandler} covers validation and generic failures.
 */
@RestControllerAdvice
public class GatewayExceptionHandler {

    /**
     * Downstream services returned a non-2xx response; forward the same status code and
     * body verbatim so the caller sees the standardized error from the origin service.
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<byte[]> handleDownstreamError(RestClientResponseException ex) {
        HttpHeaders headers = new HttpHeaders();
        MediaType contentType = ex.getResponseHeaders() != null ? ex.getResponseHeaders().getContentType() : null;
        headers.setContentType(contentType != null ? contentType : MediaType.APPLICATION_JSON);
        return ResponseEntity.status(ex.getStatusCode()).headers(headers).body(ex.getResponseBodyAsByteArray());
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiError> handleUnreachable(ResourceAccessException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "Service Unavailable",
                        "A required service is temporarily unavailable. Please try again."));
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiError> handleCircuitOpen(CallNotPermittedException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "Service Unavailable",
                        "Service temporarily unavailable; try again shortly."));
    }
}
