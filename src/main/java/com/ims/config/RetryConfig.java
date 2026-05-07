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

  @Bean
  public RetryTemplate retryTemplate() {
    RetryTemplate template = new RetryTemplate();

    ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
    backOffPolicy.setInitialInterval(200);
    backOffPolicy.setMultiplier(2);
    backOffPolicy.setMaxInterval(1000);

    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
    retryPolicy.setMaxAttempts(3);

    template.setBackOffPolicy(backOffPolicy);
    template.setRetryPolicy(retryPolicy);

    return template;
  }
}
