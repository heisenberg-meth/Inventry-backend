package com.ims.config;

import com.ims.shared.auth.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateTenantResolver implements CurrentTenantIdentifierResolver<Long> {

  @Override
  public Long resolveCurrentTenantIdentifier() {
    Long tenantId = TenantContext.get();
    if (tenantId == null) {
      // In multi-tenant environments, we must always have a tenant context
      // to prevent cross-tenant data leakage or DB constraint failures.
      throw new IllegalStateException("Tenant context is missing. Cannot resolve current tenant identifier.");
    }
    return tenantId;
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return false;
  }
}
