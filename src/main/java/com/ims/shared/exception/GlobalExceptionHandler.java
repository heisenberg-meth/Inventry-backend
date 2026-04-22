package com.ims.shared.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  private static final int STATUS_BAD_REQUEST = 400;
  private static final int STATUS_NOT_FOUND = 404;
  private static final int STATUS_FORBIDDEN = 403;
  private static final int STATUS_UNPROCESSABLE = 422;
  private static final int STATUS_CONFLICT = 409;
  private static final int STATUS_UNAUTHORIZED = 401;
  private static final int STATUS_INTERNAL_ERROR = 500;


  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> fieldErrors = new HashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(err -> fieldErrors.put(err.getField(), err.getDefaultMessage()));

    Map<String, Object> body =
        errorBody(
            STATUS_BAD_REQUEST, "VALIDATION_FAILED", "Validation failed", request.getRequestURI());
    body.put("fields", fieldErrors);
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(
      EntityNotFoundException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(errorBody(STATUS_NOT_FOUND, "NOT_FOUND", ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleResourceNotFound(
      ResourceNotFoundException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(errorBody(STATUS_NOT_FOUND, "NOT_FOUND", ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(errorBody(STATUS_FORBIDDEN, "FORBIDDEN", ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<Map<String, Object>> handleUnauthorized(
      UnauthorizedException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(errorBody(STATUS_UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(InsufficientStockException.class)
  public ResponseEntity<Map<String, Object>> handleInsufficientStock(
      InsufficientStockException ex, HttpServletRequest request) {
    Map<String, Object> body =
        errorBody(STATUS_UNPROCESSABLE, "INSUFFICIENT_STOCK", ex.getMessage(), request.getRequestURI());
    body.put("available_stock", ex.getAvailableStock());
    body.put("requested_qty", ex.getRequestedQty());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> handleDataIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            errorBody(
                STATUS_CONFLICT, "CONFLICT", "Data integrity violation", request.getRequestURI()));
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<Map<String, Object>> handleBadRequestException(
      BadRequestException ex, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(errorBody(STATUS_BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleBadRequest(
      IllegalArgumentException ex, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(errorBody(STATUS_BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalState(
      IllegalStateException ex, HttpServletRequest request) {
    log.warn("Illegal state encountered at {}: {}", request.getRequestURI(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(errorBody(STATUS_CONFLICT, "ILLEGAL_STATE", ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNoResourceFound(
      org.springframework.web.servlet.resource.NoResourceFoundException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(errorBody(STATUS_NOT_FOUND, "NOT_FOUND", "Resource not found", request.getRequestURI()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleAll(Exception ex, HttpServletRequest request) {
    log.error("Unexpected error occurred at [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
    
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            errorBody(
                STATUS_INTERNAL_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request.getRequestURI()));
  }

  private Map<String, Object> errorBody(int status, String error, String message, String path) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", status);
    body.put("error", error);
    body.put("message", message);
    body.put("path", path);
    body.put("timestamp", LocalDateTime.now().toString());
    body.put("correlation_id", org.slf4j.MDC.get("correlation_id"));
    return body;
  }
}
