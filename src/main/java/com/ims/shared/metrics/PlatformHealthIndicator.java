package com.ims.shared.metrics;

import java.sql.Connection;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(RedisTemplate.class)
@Slf4j
public class PlatformHealthIndicator implements HealthIndicator {

  private final DataSource dataSource;
  private final RedisTemplate<String, Object> redisTemplate;

  public PlatformHealthIndicator(DataSource dataSource, RedisTemplate<String, Object> redisTemplate) {
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
    try {
      if (redisTemplate == null) {
        log.warn("RedisTemplate not available - marking Redis as DOWN but continuing");
        return false;
      }
      var connectionFactory = redisTemplate.getConnectionFactory();
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