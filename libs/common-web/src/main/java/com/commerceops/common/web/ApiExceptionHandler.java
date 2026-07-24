package com.commerceops.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(
                        ex.getStatus().value(),
                        ex.getError(),
                        ex.getMessage(),
                        ex.getDetails(),
                        path(request),
                        null
                ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() != null && !ex.getReason().isBlank()
                ? ex.getReason()
                : defaultMessage(status);
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message, null, path(request), null));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class
    })
    public ResponseEntity<ApiError> handleValidation(Exception ex, HttpServletRequest request) {
        List<String> details;
        if (ex instanceof MethodArgumentNotValidException manv) {
            details = manv.getBindingResult().getFieldErrors().stream()
                    .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                    .toList();
        } else {
            BindException bind = (BindException) ex;
            details = bind.getBindingResult().getFieldErrors().stream()
                    .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                    .toList();
        }
        String message = details.isEmpty()
                ? "Please check your details and try again."
                : "Please check your details and try again.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "Bad Request",
                        message,
                        details,
                        path(request),
                        null
                ));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        String message = ex instanceof IllegalArgumentException && ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Please check your details and try again.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "Bad Request",
                        message,
                        null,
                        path(request),
                        null
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal Server Error",
                        "Something went wrong on our side. Please try again in a moment.",
                        null,
                        path(request),
                        null
                ));
    }

    private static String path(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : null;
    }

    private static String defaultMessage(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "We couldn’t find what you were looking for.";
            case UNAUTHORIZED -> "Please sign in to continue.";
            case FORBIDDEN -> "You don’t have permission to do that.";
            case CONFLICT -> "That action conflicts with the current state. Refresh and try again.";
            case TOO_MANY_REQUESTS -> "Too many requests. Please wait a moment and try again.";
            case SERVICE_UNAVAILABLE -> "A required service is temporarily unavailable. Please try again.";
            default -> status.is4xxClientError()
                    ? "Please check your details and try again."
                    : "Something went wrong on our side. Please try again in a moment.";
        };
    }
}
