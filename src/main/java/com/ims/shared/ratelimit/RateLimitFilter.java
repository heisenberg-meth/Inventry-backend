package com.ims.shared.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                     HttpServletResponse res,
                                     FilterChain chain) throws ServletException, IOException {
        String ip = req.getRemoteAddr();
        boolean authenticated = req.getHeader("Authorization") != null;
        int limit = authenticated ? 500 : 100;

        String key = "rate:" + ip + ":" + (System.currentTimeMillis() / 60000);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }

        res.addHeader("X-RateLimit-Limit", String.valueOf(limit));
        res.addHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - (count != null ? count : 0))));

        if (count != null && count > limit) {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Too Many Requests\",\"retry_after\":60}");
            return;
        }

        chain.doFilter(req, res);
    }
}
