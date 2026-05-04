package com.ims.config;

import com.ims.shared.auth.TenantContext;
import com.ims.shared.cache.TenantAwareCacheWrapper;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Cache configuration that works in all profiles (including test).
 * Uses TenantAwareCacheWrapper to provide tenant-isolation via key prefixing.
 * For tests, use a simple CacheManager (like Caffeine or ConcurrentMap).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Tenant-aware cache resolver that wraps caches with tenant key prefixing.
     * Works with any CacheManager implementation (Redis, Caffeine, etc.).
     */
    @Bean
    public CacheResolver tenantAwareCacheResolver(@NonNull CacheManager cacheManager) {
        return new CacheResolver() {
            @Override
            public @NonNull Collection<? extends Cache> resolveCaches(
                    @NonNull CacheOperationInvocationContext<?> context) {

                Long tenantId = TenantContext.getTenantId();

                return context.getOperation().getCacheNames().stream()
                        .map(cacheManager::getCache)
                        .filter(Objects::nonNull)
                        .map(cache -> new TenantAwareCacheWrapper(cache, tenantId))
                        .collect(Collectors.toList());
            }
        };
    }
}
