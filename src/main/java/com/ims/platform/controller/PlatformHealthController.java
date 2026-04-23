package com.ims.platform.controller;

import com.ims.shared.rbac.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/health")
@RequiredArgsConstructor
@Tag(name = "Platform - Health", description = "Extended system health monitoring")
@SecurityRequirement(name = "bearerAuth")
public class PlatformHealthController {

    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/extended")
    @RequiresRole({"ROOT"})
    @Operation(summary = "Deep health check", description = "Checks DB, Redis, and Disk space")
    public ResponseEntity<Map<String, Object>> getExtendedHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        
        // 1. Database Health
        try (java.sql.Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            health.put("database", Map.of(
                "status", valid ? "UP" : "DOWN",
                "message", valid ? "Connection successful and valid" : "Connection invalid"
            ));
        } catch (Exception e) {
            health.put("database", Map.of("status", "DOWN", "error", e.getMessage()));
        }

        // 2. Redis Health
        try {
            redisTemplate.execute((RedisConnection connection) -> {
                return connection.serverCommands().info();
            });
            health.put("redis", Map.of("status", "UP"));
        } catch (Exception e) {
            health.put("redis", Map.of("status", "DOWN", "error", e.getMessage()));
        }

        // 3. Disk Space
        File root = new File("/");
        long total = root.getTotalSpace();
        long free = root.getUsableSpace();
        health.put("disk", Map.of(
            "total_gb", total / (1024 * 1024 * 1024),
            "free_gb", free / (1024 * 1024 * 1024),
            "usage_percent", total > 0 ? (double) (total - free) / total * 100 : 0
        ));

        health.put("system_time", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(health);
    }
}
