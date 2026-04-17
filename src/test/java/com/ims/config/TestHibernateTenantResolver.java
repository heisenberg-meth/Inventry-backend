package com.ims.config;

import com.ims.shared.auth.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestHibernateTenantResolver implements CurrentTenantIdentifierResolver<Long> {

  private static final Long DEFAULT_TENANT_ID = 1L;

  @Override
  public Long resolveCurrentTenantIdentifier() {
    Long tenantId = TenantContext.get();
    // In tests, if no tenant is set, we return the default seeded tenant ID.
    // This allows tests that don't explicitly set a tenant to still function
    // in a multi-tenant environment.
    return (tenantId != null) ? tenantId : DEFAULT_TENANT_ID;
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return false;
  }
}
