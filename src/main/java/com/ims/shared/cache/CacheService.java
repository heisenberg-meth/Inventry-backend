package com.ims.shared.cache;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CacheService {

  private final RedisTemplate<String, Object> redisTemplate;

  public Object get(@NonNull String key) {
    return redisTemplate.opsForValue().get(key);
  }

  public void set(@NonNull String key, @NonNull Object value, long ttl, @NonNull TimeUnit unit) {
    redisTemplate.opsForValue().set(key, value, ttl, unit);
  }

  public void evict(@NonNull String key) {
    redisTemplate.delete(key);
  }

  public void evictByPattern(@NonNull String pattern) {
    var keys = redisTemplate.keys(pattern);
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }
}
