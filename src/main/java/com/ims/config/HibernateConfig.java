package com.ims.config;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
@RequiredArgsConstructor
public class HibernateConfig {

  private final TenantLeakInterceptor tenantLeakInterceptor;

  @Bean
  @Profile("test")
  public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
    return (Map<String, Object> hibernateProperties) -> 
        hibernateProperties.put("hibernate.session_factory.statement_inspector", tenantLeakInterceptor);
  }
}
