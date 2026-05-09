package com.ims.config;

import com.ims.shared.auth.TenantContext;
import com.ims.shared.cache.TenantAwareCacheWrapper;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Cache configuration that works in all profiles (including test). Uses TenantAwareCacheWrapper to
 * provide tenant-isolation via key prefixing. For tests, use a simple CacheManager (like Caffeine
 * or ConcurrentMap).
 */
@Configuration
@EnableCaching
public class CacheConfig {

  private static final Set<String> PLATFORM_CACHE_NAMES =
      Set.of("platform-subscriptions", "system-config");

  /**
   * Tenant-aware cache resolver that wraps caches with tenant key prefixing. Works with any
   * CacheManager implementation (Redis, Caffeine, etc.).
   */
  @Bean
  public CacheResolver tenantAwareCacheResolver(
      CacheManager cacheManager, @Nullable StringRedisTemplate stringRedisTemplate) {
    return new CacheResolver() {
      @Override
      public @NonNull Collection<? extends Cache> resolveCaches(
          @NonNull CacheOperationInvocationContext<?> context) {

        Long tenantId = TenantContext.getTenantId();

        return context.getOperation().getCacheNames().stream()
            .map(
                cacheName -> {
                  Cache cache = cacheManager.getCache(cacheName);
                  if (cache == null) return null;

                  boolean isPlatformCache = PLATFORM_CACHE_NAMES.contains(cacheName);
                  Long cacheTenantId = isPlatformCache ? null : tenantId;

                  return new TenantAwareCacheWrapper(
                      cache, cacheTenantId, isPlatformCache, stringRedisTemplate);
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
      }
    };
  }
}
