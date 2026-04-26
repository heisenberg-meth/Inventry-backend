package com.ims;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.flyway.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.task.scheduling.enabled=false",
      "spring.testcontainers.enabled=false",
      "spring.jpa.properties.hibernate.multiTenancy=NONE"
    })
@ActiveProfiles("test")
class ImsApplicationTests {
  @MockitoBean private RedisTemplate<String, Object> redisTemplate;

  @MockitoBean private JavaMailSender javaMailSender;

  @Test
  void contextLoads() {}
}
