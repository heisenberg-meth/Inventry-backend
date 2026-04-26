package com.ims.shared.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator that proactively validates the Redis connection.
 * Reports DOWN immediately, rather than waiting for the first cache miss.
 */
@Component
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Health health() {
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            if ("PONG".equals(pong)) {
                return Health.up().withDetail("redis", "available").build();
            }
            return Health.down().withDetail("redis", "unexpected ping response: " + pong).build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("redis", "unavailable")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
