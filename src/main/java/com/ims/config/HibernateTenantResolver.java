package com.ims.config;

import com.ims.shared.auth.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateTenantResolver implements CurrentTenantIdentifierResolver<Long> {

  @Override
  public Long resolveCurrentTenantIdentifier() {
    Long tenantId = TenantContext.get();
    return tenantId; // Return null if not set, instead of 0L
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return false;
  }
}
