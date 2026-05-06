package com.ims.shared.ratelimit;

import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LUA_SCRIPT = "local key = KEYS[1] " +
            "local now = tonumber(ARGV[1]) " +
            "local window = tonumber(ARGV[2]) " +
            "local limit = tonumber(ARGV[3]) " +
            "local clearBefore = now - window " +
            "redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore) " +
            "local current = redis.call('ZCARD', key) " +
            "if current >= limit then " +
            "  return 0 " +
            "else " +
            "  redis.call('ZADD', key, now, now) " +
            "  redis.call('EXPIRE', key, math.ceil(window / 1000)) " +
            "  return 1 " +
            "end";

    private final RedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    /**
     * Checks if a request is allowed under the rate limit.
     *
     * @param key           Redis key for the rate limit
     * @param limit         Maximum number of requests in the window
     * @param windowSeconds Duration of the sliding window in seconds
     * @return true if allowed, false if rate limited
     */
    public boolean isAllowed(String key, int limit, int windowSeconds) {
        try {
            long now = System.currentTimeMillis();
            long windowMillis = (long) windowSeconds * 1000;

            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    String.valueOf(now),
                    String.valueOf(windowMillis),
                    String.valueOf(limit));

            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("Rate limiter Redis failure for key {}: {}", key, e.getMessage());
            // Fail open to avoid blocking legitimate traffic during Redis outage
            return true;
        }
    }

    /**
     * Gets the current request count in the window.
     * Note: This is not atomic with isAllowed and should only be used for headers.
     */
    public int getCount(String key) {
        try {
            Long count = redisTemplate.opsForZSet().zCard(key);
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
