package com.ims.config;

import com.ims.shared.auth.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Resolves the current tenant identifier for Hibernate when a tenant-filtered query runs.
 *
 * <p>Returns a sentinel ({@code 0L}) when no {@link TenantContext} is set — this is intentional.
 * Spring Data JPA validates derived queries (e.g. {@code findByTenantId}) at startup by opening a
 * SessionFactory probe, which calls this resolver before any HTTP request has set a tenant. We do
 * not want to fail bean creation in that case. The {@code 0L} sentinel will not match any real
 * tenant row, so any genuinely-tenant-leaky query at runtime returns no rows rather than crashing
 * the application.
 *
 * <p>Public endpoints (signup, login, verify-email) intentionally run without a tenant. They use
 * {@code findByEmailUnfiltered} or {@code findByCompanyCode} repository methods that bypass the
 * tenant filter, so this sentinel never affects them.
 */
@Component
@Profile("!test")
public class HibernateTenantResolver implements CurrentTenantIdentifierResolver<Long> {

  @Override
  public Long resolveCurrentTenantIdentifier() {
    Long tenantId = TenantContext.getTenantId();
    // Use the explicit platform identifier if no tenant is set.
    // This allows startup probes and platform operations to proceed safely.
    return tenantId != null ? tenantId : TenantContext.PLATFORM_TENANT_ID;
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return true;
  }
}
