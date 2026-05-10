package com.ims.shared.metrics;

import java.sql.Connection;
import java.util.Optional;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PlatformHealthIndicator implements HealthIndicator {

  private final DataSource dataSource;
  private final Optional<RedisTemplate<String, Object>> redisTemplate;

  public PlatformHealthIndicator(
      DataSource dataSource, Optional<RedisTemplate<String, Object>> redisTemplate) {
    this.dataSource = dataSource;
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Health health() {
    Health.Builder builder = new Health.Builder();

    boolean dbHealthy = checkDatabase();
    boolean redisHealthy = checkRedis();

    if (dbHealthy && redisHealthy) {
      return builder.up().withDetail("database", "UP").withDetail("redis", "UP").build();
    } else {
      return builder
          .down()
          .withDetail("database", dbHealthy ? "UP" : "DOWN")
          .withDetail("redis", redisHealthy ? "UP" : "DOWN")
          .build();
    }
  }

  private boolean checkDatabase() {
    try (Connection connection = dataSource.getConnection()) {
      return connection.isValid(2);
    } catch (Exception e) {
      log.error("Database health check failed: {}", e.getMessage());
      return false;
    }
  }

  private boolean checkRedis() {
    if (redisTemplate.isEmpty()) {
      return true; // Redis is disabled, so it's not "unhealthy"
    }
    try {
      var template = redisTemplate.get();
      var connectionFactory = template.getConnectionFactory();
      if (connectionFactory == null) {
        log.error("Redis connection factory is null");
        return false;
      }
      String result = connectionFactory.getConnection().ping();
      return "PONG".equals(result);
    } catch (Exception e) {
      log.error("Redis health check failed: {}", e.getMessage());
      return false;
    }
  }
}
