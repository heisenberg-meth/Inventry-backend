package com.ims.shared.cache;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CacheService {

  private final RedisTemplate<String, Object> redisTemplate;

  public Object get(String key) {
    return redisTemplate.opsForValue().get(key);
  }

  public void set(String key, Object value, long ttl, TimeUnit unit) {
    redisTemplate.opsForValue().set(key, value, ttl, unit);
  }

  public void evict(String key) {
    redisTemplate.delete(key);
  }

  public void evictByPattern(String pattern) {
    var keys = redisTemplate.keys(pattern);
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }
}
