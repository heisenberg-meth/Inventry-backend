package com.ims.config;

import com.ims.shared.ratelimit.RateLimiterService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.util.HashSet;
import java.util.Set;

@TestConfiguration
@Profile("test")
public class TestRedisConfig {

  @Bean
  @Primary
  public RedisConnectionFactory redisConnectionFactory() {
    RedisConnectionFactory factory = Mockito.mock(RedisConnectionFactory.class);
    RedisConnection connection = Mockito.mock(RedisConnection.class);
    RedisKeyCommands keyCommands = Mockito.mock(RedisKeyCommands.class);
    RedisServerCommands serverCommands = Mockito.mock(RedisServerCommands.class);

    Mockito.when(factory.getConnection()).thenReturn(connection);
    Mockito.when(connection.keyCommands()).thenReturn(keyCommands);
    Mockito.when(connection.serverCommands()).thenReturn(serverCommands);
    
    return factory;
  }

  @Bean
  @Primary
  @SuppressWarnings("unchecked")
  public RedisTemplate<String, Object> redisTemplate() {
    RedisTemplate<String, Object> template = Mockito.mock(RedisTemplate.class);
    Set<String> blacklistedTokens = new HashSet<>();
    ValueOperations<String, Object> valueOps = Mockito.mock(ValueOperations.class);

    Mockito.when(template.opsForValue()).thenReturn(valueOps);
    
    // Support blacklisting in AuthIntegrationTest
    Mockito.doAnswer(invocation -> {
        String key = invocation.getArgument(0);
        if (key.startsWith("jwt:blacklist:")) {
            blacklistedTokens.add(key);
        }
        return null;
    }).when(valueOps).set(Mockito.anyString(), Mockito.any(), Mockito.anyLong(), Mockito.any(java.util.concurrent.TimeUnit.class));

    Mockito.when(template.hasKey(Mockito.anyString())).thenAnswer(invocation -> {
        String key = invocation.getArgument(0);
        return blacklistedTokens.contains(key);
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
    Mockito.when(mock.isAllowed(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean()))
        .thenReturn(true);
    Mockito.when(mock.isAllowed(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt()))
        .thenReturn(true);
    Mockito.when(mock.getCount(Mockito.anyString())).thenReturn(0);
    return mock;
  }
}
