package com.ims;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class ImsApplication {

  public static void main(String[] args) {
    SpringApplication.run(ImsApplication.class, args);
  }

  @Bean
  @org.springframework.context.annotation.Profile("!test")
  public CommandLineRunner testRedis(RedisTemplate<String, String> redisTemplate) {
      return args -> {
          try {
              redisTemplate.opsForValue().set("test", "ok");
              System.out.println("REDIS OK");
          } catch (Exception e) {
              e.printStackTrace();
          }
      };
  }
}
