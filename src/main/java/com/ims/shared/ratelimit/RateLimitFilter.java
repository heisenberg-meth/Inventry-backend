package com.ims.shared.ratelimit;

import com.ims.shared.auth.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

  private static final int AUTH_LIMIT = 20; // Brute force protection
  private static final int PUBLIC_LIMIT = 50;
  private static final int TENANT_LIMIT = 200;
  private static final int WINDOW_SECONDS = 60;
  private static final int STATUS_TOO_MANY_REQUESTS = 429;

  private final RedisTemplate<String, Object> redisTemplate;
  private final JwtUtil jwtUtil;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest req, @NonNull HttpServletResponse res, @NonNull FilterChain chain)
      throws ServletException, IOException {
    
    String ip = req.getRemoteAddr();
    String authHeader = req.getHeader("Authorization");
    String tenantId = "none";
    
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      try {
        Long id = jwtUtil.extractTenantId(token);
        if (id != null) {
          tenantId = id.toString();
        }
      } catch (Exception e) {
        // Invalid token, treat as public
      }
    }

    String path = req.getRequestURI();
    boolean isAuthEndpoint = path.contains("/auth/");
    
    int limit;
    String key;
    if (isAuthEndpoint) {
      limit = AUTH_LIMIT;
      key = "rate:auth:" + ip;
    } else if (!"none".equals(tenantId)) {
      limit = TENANT_LIMIT;
      key = "rate:tenant:" + tenantId + ":" + ip;
    } else {
      limit = PUBLIC_LIMIT;
      key = "rate:public:" + ip;
    }

    long now = System.currentTimeMillis();
    long windowStart = now - (WINDOW_SECONDS * 1000L);

    try {
      // Sliding window using Redis Sorted Set
      redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
      redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
      redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
      
      Long count = redisTemplate.opsForZSet().zCard(key);
      int currentCount = (count != null) ? count.intValue() : 0;

      res.addHeader("X-RateLimit-Limit", String.valueOf(limit));
      res.addHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - currentCount)));

      if (currentCount > limit) {
        log.warn("Rate limit exceeded for key: {} (Limit: {})", key, limit);
        res.setStatus(STATUS_TOO_MANY_REQUESTS);
        res.setContentType("application/json");
        res.getWriter().write(String.format(
            "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again in %d seconds.\",\"retry_after\":%d}",
            WINDOW_SECONDS, WINDOW_SECONDS));
        return;
      }
    } catch (Exception e) {
      log.error("Rate limiting error for key {}: {}", key, e.getMessage());
      // Fallback: allow request if Redis is down
    }

    chain.doFilter(req, res);
  }
}
