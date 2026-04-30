package com.ims.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when the TenantLeakInterceptor detects a potential
 * cross-tenant data leak at the SQL level.
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class TenantIsolationException extends RuntimeException {
    public TenantIsolationException(String message) {
        super(message);
    }
}
