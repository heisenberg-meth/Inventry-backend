package com.ims.shared.cache;

import java.util.Set;
import java.util.concurrent.Callable;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public class TenantAwareCacheWrapper implements Cache {

  private static final String TENANT_KEY_PREFIX = "tenant:";
  private static final String PLATFORM_KEY_PREFIX = "platform:";
  private static final String CACHE_KEY_SEPARATOR = ":";
  private static final String CACHE_KEY_MIDDLE = "cache" + CACHE_KEY_SEPARATOR;

  private final Cache delegate;
  @Nullable private final Long tenantId;
  private final boolean platformCache;
  @Nullable private final StringRedisTemplate redisTemplate;

  public TenantAwareCacheWrapper(
      Cache delegate,
      @Nullable Long tenantId,
      boolean platformCache,
      @Nullable StringRedisTemplate redisTemplate) {
    Assert.notNull(delegate, "Delegate cache must not be null");
    this.delegate = delegate;
    this.tenantId = tenantId;
    this.platformCache = platformCache;
    this.redisTemplate = redisTemplate;
  }

  // Backward compatibility constructor
  public TenantAwareCacheWrapper(Cache delegate, @Nullable Long tenantId) {
    this(delegate, tenantId, false, null);
  }

  private String wrapKey(Object key) {
    Assert.notNull(key, "Cache key must not be null");
    String stringKey = String.valueOf(key);

    if (platformCache) {
      return PLATFORM_KEY_PREFIX + CACHE_KEY_MIDDLE + stringKey;
    } else {
      if (tenantId == null) {
        throw new IllegalStateException("Tenant ID is required for tenant-scoped cache access");
      }
      return TENANT_KEY_PREFIX + tenantId + CACHE_KEY_SEPARATOR + CACHE_KEY_MIDDLE + stringKey;
    }
  }

  private String getTenantKeyPattern() {
    if (platformCache) {
      return PLATFORM_KEY_PREFIX + CACHE_KEY_MIDDLE + "*";
    } else {
      if (tenantId == null) {
        throw new IllegalStateException("Tenant ID is required for tenant-scoped cache clear");
      }
      return TENANT_KEY_PREFIX + tenantId + CACHE_KEY_SEPARATOR + CACHE_KEY_MIDDLE + "*";
    }
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
    if (redisTemplate != null) {
      String keyPattern = getTenantKeyPattern();
      String fullPattern = delegate.getName() + "::" + keyPattern;
      final StringRedisTemplate redisTemplate2 = redisTemplate;
      if (redisTemplate2 != null) {
        Set<String> keys = redisTemplate2.keys(fullPattern);
        if (keys != null && !keys.isEmpty()) {
          final StringRedisTemplate redisTemplate3 = redisTemplate;
          if (redisTemplate3 != null) {
            redisTemplate3.delete(keys);
          } else {
          }
        }
      } else {
      }
    } else if (delegate instanceof RedisCache redisCache) {
      if (platformCache || tenantId != null) {
        redisCache.clear(getTenantKeyPattern());
      } else {
        delegate.clear();
      }
    } else {
      delegate.clear();
    }
  }

  @Override
  public boolean invalidate() {
    return delegate.invalidate();
  }
}
