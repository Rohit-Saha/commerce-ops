package com.commerceops.catalog.web;

import com.commerceops.catalog.service.CatalogProductNotFoundException;
import com.commerceops.common.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CatalogExceptionHandler {

    @ExceptionHandler(CatalogProductNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(CatalogProductNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(
                        HttpStatus.NOT_FOUND.value(),
                        "Not Found",
                        "That product isn’t available.",
                        null,
                        request.getRequestURI(),
                        null));
    }
}
