package com.ims;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
@ActiveProfiles("test")
class ImsApplicationTests {

  @MockitoBean
  private RedisTemplate<String, Object> redisTemplate;

  @Test
  void contextLoads() {}
}
