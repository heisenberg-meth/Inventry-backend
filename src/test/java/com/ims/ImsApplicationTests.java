package com.ims;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
    "spring.cache.type=none"
})
class ImsApplicationTests {

  @TestConfiguration
  static class TestConfig {

    @Bean
    @SuppressWarnings("unchecked")
    RedisTemplate<String, Object> redisTemplate() {
      return org.mockito.Mockito.mock(RedisTemplate.class);
    }
  }

  @Test
  void contextLoads() {
  }
}