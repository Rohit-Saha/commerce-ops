package com.commerceops.customer.web;

import com.commerceops.common.web.ApiError;
import com.commerceops.customer.service.exception.AddressNotFoundException;
import com.commerceops.customer.service.exception.CustomerNotFoundException;
import com.commerceops.customer.service.exception.EmailAlreadyRegisteredException;
import com.commerceops.customer.service.exception.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CustomerExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> handleConflict(EmailAlreadyRegisteredException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(
                        HttpStatus.CONFLICT.value(),
                        "Conflict",
                        "An account with this email already exists. Try signing in instead.",
                        null,
                        request.getRequestURI(),
                        null));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleUnauthorized(InvalidCredentialsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(
                        HttpStatus.UNAUTHORIZED.value(),
                        "Unauthorized",
                        "That email or password doesn’t match our records.",
                        null,
                        request.getRequestURI(),
                        null));
    }

    @ExceptionHandler({CustomerNotFoundException.class, AddressNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        String message = ex instanceof AddressNotFoundException
                ? "We couldn’t find that address."
                : "We couldn’t find that customer.";
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), "Not Found", message, null, request.getRequestURI(), null));
    }
}
