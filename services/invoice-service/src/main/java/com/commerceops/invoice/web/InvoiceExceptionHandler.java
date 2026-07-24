package com.commerceops.invoice.web;

import com.commerceops.common.web.ApiError;
import com.commerceops.common.web.BusinessException;
import com.commerceops.invoice.service.InvoiceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InvoiceExceptionHandler {

    @ExceptionHandler(InvoiceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(InvoiceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(
                        HttpStatus.NOT_FOUND.value(),
                        "Not Found",
                        "Invoice not found yet. Please try again shortly.",
                        null,
                        request.getRequestURI(),
                        null));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(
                        ex.getStatus().value(),
                        ex.getError(),
                        ex.getMessage(),
                        ex.getDetails(),
                        request.getRequestURI(),
                        null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> illegalState(IllegalStateException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of(
                        HttpStatus.BAD_GATEWAY.value(),
                        "Upstream Error",
                        "Unable to build the invoice right now. Please try again shortly.",
                        null,
                        request.getRequestURI(),
                        null));
    }
}
