package com.ims.shared.cache;

import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Wraps a Cache instance to provide tenant-aware key isolation.
 * Instead of creating separate caches per tenant, we prefix keys with tenantId.
 * This is scalable: cache names stay constant, only keys are isolated.
 * 
 * For RedisCache, clear() only clears keys for the current tenant prefix.
 */
public class TenantAwareCacheWrapper implements Cache {

    private final Cache delegate;
    private final Long tenantId;

    public TenantAwareCacheWrapper(@NonNull Cache delegate, Long tenantId) {
        Assert.notNull(delegate, "Delegate cache must not be null");
        this.delegate = delegate;
        this.tenantId = tenantId;
    }

    private Object wrapKey(Object key) {
        if (tenantId == null) {
            return key;
        }
        return tenantId + "::" + Objects.toString(key);
    }

    private String getTenantPrefix() {
        if (tenantId == null) {
            return "";
        }
        return tenantId + "::*";
    }

    @Override
    public @NonNull String getName() {
        return delegate.getName();
    }

    @Override
    public @NonNull Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(@NonNull Object key) {
        return delegate.get(wrapKey(key));
    }

    @Override
    public <T> T get(@NonNull Object key, @Nullable Class<T> type) {
        return delegate.get(wrapKey(key), type);
    }

    @Override
    public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
        return delegate.get(wrapKey(key), valueLoader);
    }

    @Override
    public void put(@NonNull Object key, @Nullable Object value) {
        delegate.put(wrapKey(key), value);
    }

    @Override
    public ValueWrapper putIfAbsent(@NonNull Object key, @Nullable Object value) {
        return delegate.putIfAbsent(wrapKey(key), value);
    }

    @Override
    public void evict(@NonNull Object key) {
        delegate.evict(wrapKey(key));
    }

    @Override
    public boolean evictIfPresent(@NonNull Object key) {
        return delegate.evictIfPresent(wrapKey(key));
    }

    @Override
    public void clear() {
        // Only clear keys for the current tenant, not the entire cache
        if (delegate instanceof RedisCache redisCache && tenantId != null) {
            redisCache.clear(getTenantPrefix());
        } else {
            // Fallback: clear entire cache (non-Redis or null tenantId)
            delegate.clear();
        }
    }

    @Override
    public boolean invalidate() {
        return delegate.invalidate();
    }
}
