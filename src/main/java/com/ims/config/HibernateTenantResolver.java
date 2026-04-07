package com.ims.config;

import com.ims.shared.auth.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class HibernateTenantResolver implements CurrentTenantIdentifierResolver<Long> {

  @Override
  public Long resolveCurrentTenantIdentifier() {
    Long tenantId = TenantContext.getTenantIdentifier();
    return tenantId != null ? tenantId : 0L;
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return true;
  }

  @Bean
  public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
    return (Map<String, Object> hibernateProperties) -> {
      hibernateProperties.put("hibernate.tenant_identifier_resolver", this);
      hibernateProperties.put("hibernate.multiTenancy", "PARTITION");
    };
  }
}
