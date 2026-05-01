package com.ims.shared.exception;

/**
 * Thrown when a request conflicts with existing state (e.g. duplicate email).
 * Mapped to HTTP 409 CONFLICT by {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }

  public ConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
