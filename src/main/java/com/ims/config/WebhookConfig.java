package com.ims.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

@Configuration
public class WebhookConfig {

  private static final int CONNECT_TIMEOUT_MS = 3000;
  private static final int READ_TIMEOUT_MS = 5000;
  private static final int CORE_POOL_SIZE = 5;
  private static final int MAX_POOL_SIZE = 10;
  private static final int QUEUE_CAPACITY = 50;

  @Bean
  public RestTemplate webhookRestTemplate() {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
    factory.setReadTimeout(READ_TIMEOUT_MS);
    return new RestTemplate(factory);
  }

  @Bean(name = "webhookExecutor")
  public Executor webhookExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(CORE_POOL_SIZE);
    exec.setMaxPoolSize(MAX_POOL_SIZE);
    exec.setQueueCapacity(QUEUE_CAPACITY);
    exec.setThreadNamePrefix("webhook-");
    exec.setTaskDecorator(new com.ims.shared.logging.MdcTaskDecorator());
    exec.initialize();
    return exec;
  }
}
