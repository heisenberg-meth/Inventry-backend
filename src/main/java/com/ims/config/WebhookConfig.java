package com.ims.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

@Configuration
public class WebhookConfig {

  @Bean
  public RestTemplate webhookRestTemplate() {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000); // 3s
    factory.setReadTimeout(5000); // 5s
    return new RestTemplate(factory);
  }

  @Bean(name = "webhookExecutor")
  public Executor webhookExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(5);
    exec.setMaxPoolSize(10);
    exec.setQueueCapacity(50);
    exec.setThreadNamePrefix("webhook-");
    exec.setTaskDecorator(new com.ims.shared.logging.MdcTaskDecorator());
    exec.initialize();
    return exec;
  }
}
