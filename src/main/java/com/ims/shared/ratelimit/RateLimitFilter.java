package com.ims.shared.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

  private static final int AUTH_LIMIT = 500;
  private static final int PUBLIC_LIMIT = 100;
  private static final int MILLIS_IN_MINUTE = 60000;
  private static final int SECONDS_IN_MINUTE = 60;
  private static final int STATUS_TOO_MANY_REQUESTS = 429;

  private final RedisTemplate<String, Object> redisTemplate;


  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String ip = req.getRemoteAddr();
    boolean authenticated = req.getHeader("Authorization") != null;
    int limit = authenticated ? AUTH_LIMIT : PUBLIC_LIMIT;

    String key = "rate:" + ip + ":" + (System.currentTimeMillis() / MILLIS_IN_MINUTE);
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1) {
      redisTemplate.expire(key, SECONDS_IN_MINUTE, TimeUnit.SECONDS);
    }


    res.addHeader("X-RateLimit-Limit", String.valueOf(limit));
    res.addHeader(
        "X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - (count != null ? count : 0))));

    if (count != null && count > limit) {
      res.setStatus(STATUS_TOO_MANY_REQUESTS);
      res.setContentType("application/json");
      res.getWriter()
          .write(
              "{\"error\":\"Too Many Requests\",\"retry_after\":" + SECONDS_IN_MINUTE + "}");
      return;
    }


    chain.doFilter(req, res);
  }
}
