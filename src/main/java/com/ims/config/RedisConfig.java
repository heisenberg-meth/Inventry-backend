package com.ims.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis-specific configuration.
 * Active only when Redis is available (not in test profile by default).
 * The tenant-aware cache resolver is in CacheConfig (active in all profiles).
 */
@Configuration
@Profile("!test")
public class RedisConfig {

    private static final int TTL_PRODUCTS_MINUTES = 15;
    private static final int TTL_STOCK_MINUTES = 5;
    private static final int TTL_REPORTS_MINUTES = 30;
    private static final int TTL_TENANT_HOURS = 1;

    // Reuse serializer instance - don't recreate per call
    private final GenericJackson2JsonRedisSerializer serializer = createSerializer();

    private GenericJackson2JsonRedisSerializer createSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put("products", ttl(Duration.ofMinutes(TTL_PRODUCTS_MINUTES)));
        configs.put("categories", ttl(Duration.ofMinutes(TTL_PRODUCTS_MINUTES)));
        configs.put("stock", ttl(Duration.ofMinutes(TTL_STOCK_MINUTES)));
        configs.put("reports", ttl(Duration.ofMinutes(TTL_REPORTS_MINUTES)));
        configs.put("tenant", ttl(Duration.ofHours(TTL_TENANT_HOURS)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(ttl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(configs)
                .build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        return template;
    }

    private RedisCacheConfiguration ttl(Duration duration) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(duration)
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }
}
