package com.ims.shared.exception;

/**
 * Thrown when an infrastructure dependency (DB, cache, etc.) is unavailable.
 * Mapped to HTTP 503 SERVICE_UNAVAILABLE by {@link GlobalExceptionHandler}.
 */
public class ServiceUnavailableException extends RuntimeException {

  public ServiceUnavailableException(String message) {
    super(message);
  }

  public ServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
