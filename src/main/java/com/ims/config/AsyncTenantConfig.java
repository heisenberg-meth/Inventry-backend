package com.ims.config;

import com.ims.shared.logging.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncTenantConfig {

  @Bean
  public TaskDecorator tenantTaskDecorator() {
    return new MdcTaskDecorator();
  }
}
