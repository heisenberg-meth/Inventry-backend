package com.ims.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class RedisConfig {

  private static final int TTL_PRODUCTS_MINUTES = 15;
  private static final int TTL_STOCK_MINUTES = 5;
  private static final int TTL_REPORTS_MINUTES = 30;
  private static final int TTL_TENANT_HOURS = 1;


  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
    Map<String, RedisCacheConfiguration> configs = new HashMap<>();
    configs.put("products", ttl(Duration.ofMinutes(TTL_PRODUCTS_MINUTES)));
    configs.put("stock", ttl(Duration.ofMinutes(TTL_STOCK_MINUTES)));
    configs.put("reports", ttl(Duration.ofMinutes(TTL_REPORTS_MINUTES)));
    configs.put("tenant", ttl(Duration.ofHours(TTL_TENANT_HOURS)));


    return RedisCacheManager.builder(factory).withInitialCacheConfigurations(configs).build();
  }

  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
    return template;
  }

  private RedisCacheConfiguration ttl(Duration duration) {
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(duration)
        .serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(
                new GenericJackson2JsonRedisSerializer()));
  }
}
