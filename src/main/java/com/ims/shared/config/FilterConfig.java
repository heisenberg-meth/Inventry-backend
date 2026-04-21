package com.ims.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
public class FilterConfig {

    /**
     * Extracts and validates headers from trusted proxies (X-Forwarded-For, X-Forwarded-Proto).
     * Works in tandem with RateLimitFilter to ensure rate limits are applied to the true client IP.
     */
    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}
