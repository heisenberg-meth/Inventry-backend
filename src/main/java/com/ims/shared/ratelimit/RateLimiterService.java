package com.ims.shared.ratelimit;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Counter authRateLimitViolations;
    private final Counter publicRateLimitViolations;
    private final Counter authenticatedRateLimitViolations;
    private final Counter tenantRateLimitViolations;
    private final Counter redisFailures;
    private final Timer redisLatencyTimer;

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

    public RateLimiterService(RedisTemplate<String, Object> redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.authRateLimitViolations = Counter.builder("ratelimit.violations").tag("tier", "auth")
                .register(meterRegistry);
        this.publicRateLimitViolations = Counter.builder("ratelimit.violations").tag("tier", "public")
                .register(meterRegistry);
        this.authenticatedRateLimitViolations = Counter.builder("ratelimit.violations").tag("tier", "authenticated")
                .register(meterRegistry);
        this.tenantRateLimitViolations = Counter.builder("ratelimit.violations").tag("tier", "tenant")
                .register(meterRegistry);
        this.redisFailures = Counter.builder("ratelimit.redis.failures").register(meterRegistry);
        this.redisLatencyTimer = Timer.builder("ratelimit.redis.latency").register(meterRegistry);
    }

    /**
     * Checks if a request is allowed under the rate limit.
     *
     * @param key           Redis key for the rate limit
     * @param limit         Maximum number of requests in the window
     * @param windowSeconds Duration of the sliding window in seconds
     * @return true if allowed, false if rate limited
     */
    public boolean isAllowed(String key, int limit, int windowSeconds) {
        return isAllowed(key, limit, windowSeconds, false);
    }

    @CircuitBreaker(name = "redisService", fallbackMethod = "redisFallback")
    public boolean isAllowed(String key, int limit, int windowSeconds, boolean failClosed) {
        return redisLatencyTimer.record(() -> {
            long now = System.currentTimeMillis();
            long windowMillis = (long) windowSeconds * 1000;

            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    String.valueOf(now),
                    String.valueOf(windowMillis),
                    String.valueOf(limit));

            boolean allowed = result != null && result == 1L;

            if (!allowed) {
                incrementViolationCounter(key);
            }

            return allowed;
        });
    }

    public boolean redisFallback(String key, int limit, int windowSeconds, boolean failClosed, Throwable t) {
        log.warn("Circuit breaker OPEN for redisService - rate limiter unavailable for key {}", key);
        redisFailures.increment();
        return !failClosed;
    }

    private void incrementViolationCounter(String key) {
        if (key.contains("rate_limit:auth:")) {
            authRateLimitViolations.increment();
        } else if (key.contains("rate_limit:public:")) {
            publicRateLimitViolations.increment();
        } else if (key.contains("rate_limit:user:")) {
            authenticatedRateLimitViolations.increment();
        } else if (key.contains("rate_limit:tenant:")) {
            tenantRateLimitViolations.increment();
        }
    }

    /**
     * Gets the current request count in the window.
     * Note: This is not atomic with isAllowed and should only be used for headers.
     */
    @CircuitBreaker(name = "redisService", fallbackMethod = "getCountFallback")
    public int getCount(String key) {
        try {
            Long count = redisTemplate.opsForZSet().zCard(key);
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getCountFallback(String key, Throwable t) {
        return 0;
    }
}
