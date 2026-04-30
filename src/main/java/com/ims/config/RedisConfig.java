package com.ims.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ims.shared.auth.TenantContext;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@EnableCaching
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class RedisConfig {

  private static final int TTL_PRODUCTS_MINUTES = 15;
  private static final int TTL_STOCK_MINUTES = 5;
  private static final int TTL_REPORTS_MINUTES = 30;
  private static final int TTL_TENANT_HOURS = 1;
  private static final int TTL_PERMISSIONS_MINUTES = 5;
  private static final int TTL_DEFAULT_MINUTES = 10;

  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
    RedisConnectionFactory f = Objects.requireNonNull(factory);
    Map<String, RedisCacheConfiguration> configs = new HashMap<>();
    configs.put("products", ttl(Objects.requireNonNull(Duration.ofMinutes(TTL_PRODUCTS_MINUTES))));
    configs.put("categories", ttl(Objects.requireNonNull(Duration.ofMinutes(TTL_PRODUCTS_MINUTES))));
    configs.put("stock", ttl(Objects.requireNonNull(Duration.ofMinutes(TTL_STOCK_MINUTES))));
    configs.put("reports", ttl(Objects.requireNonNull(Duration.ofMinutes(TTL_REPORTS_MINUTES))));
    configs.put("tenant", ttl(Objects.requireNonNull(Duration.ofHours(TTL_TENANT_HOURS))));
    configs.put("permissions", ttl(Objects.requireNonNull(Duration.ofMinutes(TTL_PERMISSIONS_MINUTES))));

    RedisCacheConfiguration tmp = ttl(Objects.requireNonNull(Duration.ofMinutes(TTL_DEFAULT_MINUTES)));

    RedisCacheConfiguration defaultConfig = Objects.requireNonNull(tmp);

    return RedisCacheManager.builder(f)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(configs)
        .build();
  }

  @Bean
  public CacheResolver tenantAwareCacheResolver(CacheManager cacheManager) {
    return new CacheResolver() {

      @Override
      public Collection<? extends Cache> resolveCaches(
          CacheOperationInvocationContext<?> context) {
        Long tenantId = TenantContext.getTenantId();
        Collection<String> cacheNames = context.getOperation().getCacheNames();

        String tenantSuffix = Objects.requireNonNull(tenantId != null ? tenantId.toString() : "default");

        Collection<? extends Cache> result = cacheNames.stream()
            .map(name -> Objects.requireNonNull(name) + ":" + tenantSuffix)
            .map(
                cacheName -> {
                  Cache cache = cacheManager.getCache(Objects.requireNonNull(cacheName));
                  if (cache == null) {
                    String[] parts = Objects.requireNonNull(cacheName.split(":"));
                    String baseName = Objects.requireNonNull(parts[0]);
                    return cacheManager.getCache(baseName);
                  }
                  return cache;
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        return Objects.requireNonNull(result);
      }
    };
  }

  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(Objects.requireNonNull(jsonSerializer()));
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(Objects.requireNonNull(jsonSerializer()));
    return template;
  }

  private GenericJackson2JsonRedisSerializer jsonSerializer() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());

    return Objects.requireNonNull(new GenericJackson2JsonRedisSerializer(mapper));
  }

  private RedisCacheConfiguration ttl(Duration duration) {
    Duration d = Objects.requireNonNull(duration);

    RedisSerializer<Object> serializer = Objects.requireNonNull(jsonSerializer());

    return Objects.requireNonNull(
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(d)
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)));
  }
}
