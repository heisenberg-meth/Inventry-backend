package com.ims.shared.auth;

import com.ims.shared.exception.TenantContextException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Centralized component to access information from the Spring Security context.
 * Replaces redundant tenant and user extraction blocks across services.
 */
@Component
@Slf4j
public class SecurityContextAccessor {

  /**
   * Retrieves the current tenant ID from the security context.
   * Throws an exception if not found, as most service operations require a tenant.
   */
  public Long requireTenantId() {
    return getTenantId()
        .orElseThrow(() -> new TenantContextException("Tenant context missing from security context"));
  }

  /**
   * Retrieves the current tenant ID if present.
   */
  public Optional<Long> getTenantId() {
    return getDetails().map(JwtAuthDetails::getTenantId);
  }

  /**
   * Retrieves the current user ID.
   */
  public Optional<Long> getUserId() {
    return getDetails().map(JwtAuthDetails::getUserId);
  }

  /**
   * Retrieves the business type of the current tenant (e.g., PHARMACY, WAREHOUSE).
   */
  public Optional<String> getBusinessType() {
    return getDetails().map(JwtAuthDetails::getBusinessType);
  }

  /**
   * Checks if the current session is an impersonation session by a ROOT user.
   */
  public boolean isImpersonation() {
    return getDetails().map(JwtAuthDetails::isImpersonation).orElse(false);
  }

  private Optional<JwtAuthDetails> getDetails() {
    try {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
        return Optional.of(details);
      }
    } catch (Exception e) {
      log.trace("Error accessing security details: {}", e.getMessage());
    }
    return Optional.empty();
  }
}
