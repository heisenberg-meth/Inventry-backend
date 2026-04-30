package com.ims.shared.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String TRACE_ID_KEY = "traceId";

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        HttpStatus status = ex.getMessage().toLowerCase().contains("not authenticated")
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.FORBIDDEN;
        return buildResponse(ex.getMessage(), status == HttpStatus.UNAUTHORIZED ? "UNAUTHORIZED" : "ACCESS_DENIED",
                status, request, null);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return buildResponse(ex.getMessage(), "NOT_FOUND", HttpStatus.NOT_FOUND, request, null);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleInsufficientStock(InsufficientStockException ex, HttpServletRequest request) {
        Map<String, String> details = new HashMap<>();
        details.put("available", String.valueOf(ex.getAvailableStock()));
        details.put("requested", String.valueOf(ex.getRequestedQuantity()));
        return buildResponse(ex.getMessage(), "INSUFFICIENT_STOCK", HttpStatus.UNPROCESSABLE_ENTITY, request, details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponse(ex.getMessage(), "BAD_REQUEST", HttpStatus.BAD_REQUEST, request, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(IllegalStateException ex, HttpServletRequest request) {
        return buildResponse(ex.getMessage(), "STATE_CONFLICT", HttpStatus.CONFLICT, request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return buildResponse("Validation failed", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, request, errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception occurred: traceId={}", MDC.get(TRACE_ID_KEY), ex);
        return buildResponse("An unexpected error occurred. Please contact support.",
                "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, request, null);
    }

    private ResponseEntity<ApiError> buildResponse(
            String message, String code, HttpStatus status, HttpServletRequest request, Map<String, String> errors) {

        ApiError error = ApiError.builder()
                .message(message)
                .code(code)
                .status(status.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .traceId(MDC.get(TRACE_ID_KEY))
                .errors(errors)
                .build();

        return new ResponseEntity<>(error, status);
    }
}
