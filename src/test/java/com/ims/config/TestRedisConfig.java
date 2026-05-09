package com.ims.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;

/** Provides mock Redis beans for tests to prevent ApplicationContext failures. */
@TestConfiguration
@Profile("test")
public class TestRedisConfig {

  @Bean
  @Primary
  public RedisConnectionFactory redisConnectionFactory() {
    // Create a mock that implements both interfaces to satisfy standard and
    // reactive dependencies
    return Mockito.mock(
        RedisConnectionFactory.class,
        Mockito.withSettings().extraInterfaces(ReactiveRedisConnectionFactory.class));
  }

  @Bean
  @Primary
  @SuppressWarnings("unchecked")
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = Mockito.mock(RedisTemplate.class);

    // Mock ValueOperations for simple key-value lookups (used in rate limiting,
    // etc.)
    org.springframework.data.redis.core.ValueOperations<String, Object> valueOps =
        Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
    Mockito.when(template.opsForValue()).thenReturn(valueOps);

    // Mock ZSetOperations (used in sliding window rate limiting)
    org.springframework.data.redis.core.ZSetOperations<String, Object> zSetOps =
        Mockito.mock(org.springframework.data.redis.core.ZSetOperations.class);
    Mockito.when(template.opsForZSet()).thenReturn(zSetOps);

    // Default behavior for hasKey: return false (not blacklisted)
    Mockito.when(template.hasKey(Mockito.anyString())).thenReturn(false);

    return template;
  }

  @Bean
  @Primary
  @SuppressWarnings("unchecked")
  public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
      RedisConnectionFactory factory) {
    ReactiveRedisTemplate<String, Object> template = Mockito.mock(ReactiveRedisTemplate.class);

    org.springframework.data.redis.core.ReactiveValueOperations<String, Object> valueOps =
        Mockito.mock(org.springframework.data.redis.core.ReactiveValueOperations.class);
    Mockito.when(template.opsForValue()).thenReturn(valueOps);

    return template;
  }
}
