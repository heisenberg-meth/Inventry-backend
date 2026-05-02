package com.ims.shared.exception;

import java.util.Map;
import lombok.Getter;

/**
 * Thrown when a request conflicts with existing state (e.g. duplicate email).
 * Mapped to HTTP 409 CONFLICT by {@link GlobalExceptionHandler}.
 */
@Getter
public class ConflictException extends RuntimeException {
  private final Map<String, String> errors;

  public ConflictException(String message) {
    super(message);
    this.errors = null;
  }

  public ConflictException(String message, Map<String, String> errors) {
    super(message);
    this.errors = errors;
  }

  public ConflictException(String message, Throwable cause) {
    super(message, cause);
    this.errors = null;
  }
}
