package com.commerceops.inventory.web;

import com.commerceops.common.web.ApiError;
import com.commerceops.inventory.service.exception.InsufficientStockException;
import com.commerceops.inventory.service.exception.ReservationNotFoundException;
import com.commerceops.inventory.service.exception.StockItemConflictException;
import com.commerceops.inventory.service.exception.StockItemInUseException;
import com.commerceops.inventory.service.exception.StockItemNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InventoryExceptionHandler {

    @ExceptionHandler(StockItemNotFoundException.class)
    public ResponseEntity<ApiError> handleStockNotFound(StockItemNotFoundException ex, HttpServletRequest request) {
        return notFound(request, "We couldn’t find that stock item.");
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ApiError> handleReservationNotFound(ReservationNotFoundException ex, HttpServletRequest request) {
        return notFound(request, "We couldn’t find that reservation.");
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleInsufficientStock(InsufficientStockException ex, HttpServletRequest request) {
        return conflict(request, "Insufficient stock for one or more items.");
    }

    @ExceptionHandler({StockItemConflictException.class, StockItemInUseException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex, HttpServletRequest request) {
        return conflict(request, "That inventory change conflicts with the current state.");
    }

    private static ResponseEntity<ApiError> notFound(HttpServletRequest request, String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), "Not Found", message, null, request.getRequestURI(), null));
    }

    private static ResponseEntity<ApiError> conflict(HttpServletRequest request, String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "Conflict", message, null, request.getRequestURI(), null));
    }
}
