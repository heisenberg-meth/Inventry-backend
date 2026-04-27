package com.ims.config;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fallback tenant resolver for tests to prevent ApplicationContext failure.
 */
@Component
@Profile("test")
public class TestTenantResolver implements CurrentTenantIdentifierResolver<Long> {

  public static final Long TEST_TENANT_ID = -1L;

  @Override
  public Long resolveCurrentTenantIdentifier() {
    // Return a default tenant ID for tests. 
    // This satisfies Hibernate 6's requirement for a resolver when @TenantId is present.
    return TEST_TENANT_ID;
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return true;
  }
}
