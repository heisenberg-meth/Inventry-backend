package com.ims.platform.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RedisTestController {

    private final RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/redis-test")
    public String testRedis() {
        try {
            redisTemplate.opsForValue().set("test", "working");
            Object result = redisTemplate.opsForValue().get("test");
            return result != null ? result.toString() : "Null (Serialization or config issue)";
        } catch (Exception e) {
            return "Exception: " + e.getMessage() + " (Config or networking still broken)";
        }
    }
}
