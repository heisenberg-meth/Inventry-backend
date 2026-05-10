package com.ims.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Test configuration that provides a simple in-memory CacheManager. This enables cache logic
 * validation in tests without requiring Redis.
 */
@Configuration
@EnableCaching
public class TestCacheConfig {

  @Bean
  public CacheManager cacheManager() {
    // Use ConcurrentMapCacheManager for tests - simple in-memory cache
    // The tenantAwareCacheResolver from CacheConfig will wrap these caches
    return new ConcurrentMapCacheManager(
        "products", "categories", "stock", "reports", "tenant", "customers");
  }
}
