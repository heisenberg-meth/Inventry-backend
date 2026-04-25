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
import org.springframework.lang.NonNull;

@Configuration
@org.springframework.context.annotation.Profile("!test")
@EnableCaching
@EnableSpringDataWebSupport(
    pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class RedisConfig {

  private static final int TTL_PRODUCTS_MINUTES = 15;
  private static final int TTL_STOCK_MINUTES = 5;
  private static final int TTL_REPORTS_MINUTES = 30;
  private static final int TTL_TENANT_HOURS = 1;
  private static final int TTL_PERMISSIONS_MINUTES = 5;
  private static final int TTL_DEFAULT_MINUTES = 10;

  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
    final RedisConnectionFactory f = java.util.Objects.requireNonNull(factory);
    Map<String, RedisCacheConfiguration> configs = new HashMap<>();
    configs.put(
        "products",
        ttl(java.util.Objects.requireNonNull(Duration.ofMinutes(TTL_PRODUCTS_MINUTES))));
    configs.put(
        "categories",
        ttl(java.util.Objects.requireNonNull(Duration.ofMinutes(TTL_PRODUCTS_MINUTES))));
    configs.put(
        "stock", ttl(java.util.Objects.requireNonNull(Duration.ofMinutes(TTL_STOCK_MINUTES))));
    configs.put(
        "reports", ttl(java.util.Objects.requireNonNull(Duration.ofMinutes(TTL_REPORTS_MINUTES))));
    configs.put(
        "tenant", ttl(java.util.Objects.requireNonNull(Duration.ofHours(TTL_TENANT_HOURS))));
    configs.put(
        "permissions",
        ttl(java.util.Objects.requireNonNull(Duration.ofMinutes(TTL_PERMISSIONS_MINUTES))));

    RedisCacheConfiguration tmp =
        ttl(java.util.Objects.requireNonNull(Duration.ofMinutes(TTL_DEFAULT_MINUTES)));

    RedisCacheConfiguration defaultConfig = java.util.Objects.requireNonNull(tmp);

    return RedisCacheManager.builder(f)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(configs)
        .build();
  }

  @Bean
  public CacheResolver tenantAwareCacheResolver(CacheManager cacheManager) {
    return new CacheResolver() {

      @Override
      @NonNull
      public Collection<? extends Cache> resolveCaches(
          @NonNull CacheOperationInvocationContext<?> context) {
        Long tenantId = TenantContext.getTenantId();
        Collection<String> cacheNames = context.getOperation().getCacheNames();

        String tenantSuffix =
            java.util.Objects.requireNonNull(tenantId != null ? tenantId.toString() : "default");

        Collection<? extends Cache> result =
            cacheNames.stream()
                .map(name -> java.util.Objects.requireNonNull(name) + ":" + tenantSuffix)
                .map(
                    cacheName -> {
                      Cache cache =
                          cacheManager.getCache(java.util.Objects.requireNonNull(cacheName));
                      if (cache == null) {
                        String[] parts = java.util.Objects.requireNonNull(cacheName.split(":"));
                        String baseName = java.util.Objects.requireNonNull(parts[0]);
                        return cacheManager.getCache(baseName);
                      }
                      return cache;
                    })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return java.util.Objects.requireNonNull(result);
      }
    };
  }

  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(java.util.Objects.requireNonNull(jsonSerializer()));
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(java.util.Objects.requireNonNull(jsonSerializer()));
    return template;
  }

  private GenericJackson2JsonRedisSerializer jsonSerializer() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());

    // Polymorphic typing is removed for security (RCE risk).
    // DTOs should be plain Pojos with Jackson annotations if needed.

    return java.util.Objects.requireNonNull(new GenericJackson2JsonRedisSerializer(mapper));
  }

  private RedisCacheConfiguration ttl(Duration duration) {
    Duration d = java.util.Objects.requireNonNull(duration);

    org.springframework.data.redis.serializer.RedisSerializer<Object> serializer =
        java.util.Objects.requireNonNull(jsonSerializer());

    return java.util.Objects.requireNonNull(
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(d)
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)));
  }
}
