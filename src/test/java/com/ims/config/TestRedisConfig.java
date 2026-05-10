package com.ims.config;

import com.ims.shared.ratelimit.RateLimiterService;
import java.util.HashSet;
import java.util.Set;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@Configuration
public class TestRedisConfig {

  @Bean
  @Primary
  public RedisConnectionFactory redisConnectionFactory() {
    RedisConnectionFactory factory = Mockito.mock(RedisConnectionFactory.class);
    RedisConnection connection = Mockito.mock(RedisConnection.class);
    RedisKeyCommands keyCommands = Mockito.mock(RedisKeyCommands.class);
    RedisServerCommands serverCommands = Mockito.mock(RedisServerCommands.class);

    Mockito.when(factory.getConnection()).thenReturn(connection);
    Mockito.when(connection.ping()).thenReturn("PONG");
    Mockito.when(connection.keyCommands()).thenReturn(keyCommands);
    Mockito.when(connection.serverCommands()).thenReturn(serverCommands);

    return factory;
  }

  @Bean
  @Primary
  @SuppressWarnings("unchecked")
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = Mockito.mock(RedisTemplate.class);
    Set<String> blacklistedTokens = new HashSet<>();
    ValueOperations<String, Object> valueOps = Mockito.mock(ValueOperations.class);

    Mockito.when(template.opsForValue()).thenReturn(valueOps);

    // Support blacklisting in AuthIntegrationTest
    Mockito.doAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              if (key != null && key.startsWith("jwt:blacklist:")) {
                blacklistedTokens.add(key);
              }
              return null;
            })
        .when(valueOps)
        .set(
            Mockito.anyString(),
            Mockito.any(),
            Mockito.anyLong(),
            Mockito.any(java.util.concurrent.TimeUnit.class));

    Mockito.when(template.hasKey(Mockito.anyString()))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              return key != null && blacklistedTokens.contains(key);
            });

    return template;
  }

  @Bean
  @Primary
  public RedisStateCleaner redisStateCleaner() {
    return Mockito.mock(RedisStateCleaner.class);
  }

  @Bean
  @Primary
  public RateLimiterService rateLimiterService() {
    RateLimiterService mock = Mockito.mock(RateLimiterService.class);
    Mockito.when(
            mock.isAllowed(
                Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean()))
        .thenReturn(true);
    Mockito.when(mock.isAllowed(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt()))
        .thenReturn(true);
    Mockito.when(mock.getCount(Mockito.anyString())).thenReturn(0);
    return mock;
  }
}
