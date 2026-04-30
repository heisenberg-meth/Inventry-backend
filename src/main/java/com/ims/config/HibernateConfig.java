package com.ims.config;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class HibernateConfig {

  private final HibernateTenantResolver hibernateTenantResolver;
  private final Optional<TenantLeakInterceptor> tenantLeakInterceptor;

  @Bean
  public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
    return (Map<String, Object> hibernateProperties) -> {
      tenantLeakInterceptor.ifPresent(
          interceptor -> hibernateProperties.put("hibernate.session_factory.statement_inspector", interceptor));
      hibernateProperties.put("hibernate.tenant_identifier_resolver", hibernateTenantResolver);
    };
  }
}
