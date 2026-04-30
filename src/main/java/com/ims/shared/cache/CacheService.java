package com.ims.shared.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

  private final RedisTemplate<String, Object> redisTemplate;
  private static final int SCAN_BATCH_SIZE = 500;

  public Object get(String key) {
    return redisTemplate.opsForValue().get(key);
  }

  public void set(String key, Object value, long ttl, TimeUnit unit) {
    redisTemplate.opsForValue().set(key, value, ttl, unit);
  }

  public void evict(String key) {
    redisTemplate.delete(key);
  }

  /**
   * Non-blocking eviction of keys matching a pattern.
   * Uses SCAN instead of KEYS to avoid blocking the Redis event loop.
   * Implements batching and pipelining to minimize memory pressure and network
   * roundtrips.
   */
  public void evictByPattern(String pattern) {
    redisTemplate.execute((RedisCallback<Void>) connection -> {
      ScanOptions options = ScanOptions.scanOptions()
          .match(pattern)
          .count(SCAN_BATCH_SIZE)
          .build();

      try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
        List<byte[]> batch = new ArrayList<>(SCAN_BATCH_SIZE);

        while (cursor.hasNext()) {
          batch.add(cursor.next());

          if (batch.size() >= SCAN_BATCH_SIZE) {
            deleteBatch(connection, batch);
            batch.clear();
          }
        }

        if (!batch.isEmpty()) {
          deleteBatch(connection, batch);
        }
      } catch (Exception e) {
        log.warn("Cache eviction scan failed for pattern {}: {}", pattern, e.getMessage());
      }
      return null;
    });
  }

  private void deleteBatch(RedisConnection conn, List<byte[]> keys) {
    conn.openPipeline();
    for (byte[] key : keys) {
      conn.keyCommands().del(key);
    }
    conn.closePipeline();
  }
}
