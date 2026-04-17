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
    Long tenantId = TenantContext.getTenantId();
    // In tests, we prefer an explicit tenant, but fall back to default if needed.
    // However, to match the main resolver's design, we could also throw here.
    // Given Fix 1 added setupTenant() to BaseIntegrationTest, tenantId should usually be present.
    if (tenantId == null) {
      return DEFAULT_TENANT_ID;
    }
    return tenantId != null ? tenantId : 1L;
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return false;
  }
}
