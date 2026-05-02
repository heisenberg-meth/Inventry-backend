package com.ims.shared.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
public class FilterConfig {

  /**
   * Extracts and validates headers from trusted proxies (X-Forwarded-For,
   * X-Forwarded-Proto). Works
   * in tandem with RateLimitFilter to ensure rate limits are applied to the true
   * client IP.
   */
  @Bean
  public ForwardedHeaderFilter forwardedHeaderFilter() {
    return new ForwardedHeaderFilter();
  }

  @Bean
  public FilterRegistrationBean<com.ims.shared.ratelimit.RateLimitFilter> rateLimitFilterRegistration(
      com.ims.shared.ratelimit.RateLimitFilter rateLimitFilter) {
    FilterRegistrationBean<com.ims.shared.ratelimit.RateLimitFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(rateLimitFilter);
    registration.addUrlPatterns("/*");
    registration.setOrder(1); // Ensure it runs early
    return registration;
  }
}
