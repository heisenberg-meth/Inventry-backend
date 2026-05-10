package com.ims.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
@EnableRetry
public class RetryConfig {

  private static final int INITIAL_RETRY_INTERVAL_MS = 200;
  private static final int MAX_RETRY_INTERVAL_MS = 1000;
  private static final int MAX_RETRY_ATTEMPTS = 3;

  @Bean
  public RetryTemplate retryTemplate() {
    RetryTemplate template = new RetryTemplate();

    ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
    backOffPolicy.setInitialInterval(INITIAL_RETRY_INTERVAL_MS);
    backOffPolicy.setMultiplier(2);
    backOffPolicy.setMaxInterval(MAX_RETRY_INTERVAL_MS);

    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
    retryPolicy.setMaxAttempts(MAX_RETRY_ATTEMPTS);

    template.setBackOffPolicy(backOffPolicy);
    template.setRetryPolicy(retryPolicy);

    return template;
  }
}
