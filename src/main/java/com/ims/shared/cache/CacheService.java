package com.ims.shared.cache;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CacheService {

  private final Optional<RedisTemplate<String, Object>> redisTemplate;

  public Object get(String key) {
    return redisTemplate.map(rt -> rt.opsForValue().get(key)).orElse(null);
  }

  public void set(String key, Object value, long ttl, TimeUnit unit) {
    redisTemplate.ifPresent(rt -> rt.opsForValue().set(key, value, ttl, unit));
  }

  public void evict(String key) {
    redisTemplate.ifPresent(rt -> rt.delete(key));
  }

  public void evictByPattern(String pattern) {
    redisTemplate.ifPresent(
        rt -> {
          var keys = rt.keys(pattern);
          if (keys != null && !keys.isEmpty()) {
            rt.delete(keys);
          }
        });
  }
}
