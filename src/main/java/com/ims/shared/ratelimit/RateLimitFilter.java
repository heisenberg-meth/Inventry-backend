package com.ims.shared.ratelimit;

import com.ims.shared.auth.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sliding-window rate limiter backed by Redis (Valkey).
 *
 * <p>Three tiers are enforced:
 *
 * <ul>
 *   <li><b>Auth</b> — strict per-IP limit for {@code /auth/**} and {@code /api/auth/**} endpoints
 *       to mitigate credential stuffing and brute-force attacks.
 *   <li><b>Tenant</b> — generous per-tenant+IP limit for authenticated API traffic.
 *   <li><b>Public</b> — per-IP limit for any other unauthenticated traffic.
 * </ul>
 *
 * <p>Limits are configured via {@code app.rate-limit.*} properties in {@code application.yml}. If
 * Redis is unavailable the filter fails open (logs a warning and allows the request) so that a
 * cache outage does not take down the API.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

  private static final int STATUS_TOO_MANY_REQUESTS = 429;
  private static final long MILLIS_PER_SECOND = 1000L;

  /** Paths that should never be rate limited (health probes, docs, swagger assets). */
  private static final List<String> EXCLUDED_PREFIXES =
      List.of(
          "/actuator",
          "/swagger-ui",
          "/v3/api-docs",
          "/api-docs",
          "/swagger-resources",
          "/webjars",
          "/favicon.ico",
          "/error");

  /** Explicit prefixes that route to the authentication endpoints (strict brute-force tier). */
  private static final List<String> AUTH_PREFIXES = List.of("/auth", "/api/auth");

  /** Shared format string for config-validation errors; pinned by {@code RateLimitFilterTest}. */
  private static final String CONFIG_POSITIVE_MESSAGE = "%s must be >= 1 (got %d)";

  private final RedisTemplate<String, Object> redisTemplate;
  private final JwtUtil jwtUtil;
  private final int authRpm;
  private final int publicRpm;
  private final int tenantRpm;
  private final int windowSeconds;
  private final List<IpAddressMatcher> trustedProxyMatchers;

  public RateLimitFilter(
      RedisTemplate<String, Object> redisTemplate,
      JwtUtil jwtUtil,
      @Value("${app.rate-limit.auth-rpm:20}") int authRpm,
      @Value("${app.rate-limit.public-rpm:100}") int publicRpm,
      @Value("${app.rate-limit.authenticated-rpm:500}") int tenantRpm,
      @Value("${app.rate-limit.window-seconds:60}") int windowSeconds,
      @Value("${app.security.trusted-proxies:127.0.0.1,::1}") List<String> trustedProxies) {
    // Error messages reference the config property keys (not the Java field names) so operators
    // can grep the stack trace against their application.yml without a translation step.
    requirePositive("app.rate-limit.auth-rpm", authRpm);
    requirePositive("app.rate-limit.public-rpm", publicRpm);
    requirePositive("app.rate-limit.authenticated-rpm", tenantRpm);
    requirePositive("app.rate-limit.window-seconds", windowSeconds);
    this.redisTemplate = redisTemplate;
    this.jwtUtil = jwtUtil;
    this.authRpm = authRpm;
    this.publicRpm = publicRpm;
    this.tenantRpm = tenantRpm;
    this.windowSeconds = windowSeconds;
    this.trustedProxyMatchers = trustedProxies.stream().map(IpAddressMatcher::new).toList();
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    String path = normalizedPath(request);
    for (String prefix : EXCLUDED_PREFIXES) {
      if (matchesPrefix(path, prefix)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest req, @NonNull HttpServletResponse res, @NonNull FilterChain chain)
      throws ServletException, IOException {

    String clientIp = resolveClientIp(req);
    String tenantId = resolveTenantId(req);

    String path = normalizedPath(req);
    boolean isAuthEndpoint = isAuthEndpoint(path);

    int limit;
    String key;
    String tier;
    if (isAuthEndpoint) {
      limit = authRpm;
      tier = "auth";
      key = "rate:auth:" + clientIp;
    } else if (tenantId != null) {
      limit = tenantRpm;
      tier = "tenant";
      key = "rate:tenant:" + tenantId + ":" + clientIp;
    } else {
      limit = publicRpm;
      tier = "public";
      key = "rate:public:" + clientIp;
    }

    long now = System.currentTimeMillis();
    long windowStart = now - (windowSeconds * MILLIS_PER_SECOND);

    try {
      redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
      redisTemplate.opsForZSet().add(key, Objects.requireNonNull(String.valueOf(now)), now);
      redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);

      Long count = redisTemplate.opsForZSet().zCard(key);
      int currentCount = (count != null) ? count.intValue() : 0;

      res.setHeader("X-RateLimit-Limit", String.valueOf(limit));
      res.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - currentCount)));
      res.setHeader("X-RateLimit-Window-Seconds", String.valueOf(windowSeconds));

      if (currentCount > limit) {
        log.warn(
            "Rate limit exceeded (tier={}, key={}, count={}, limit={})",
            tier,
            key,
            currentCount,
            limit);
        res.setStatus(STATUS_TOO_MANY_REQUESTS);
        res.setHeader("Retry-After", String.valueOf(windowSeconds));
        res.setContentType("application/json");
        res.getWriter()
            .write(
                String.format(
                    "{\"error\":\"Too Many Requests\","
                        + "\"message\":\"Rate limit exceeded. Try again in %d seconds.\","
                        + "\"retry_after\":%d}",
                    windowSeconds, windowSeconds));
        return;
      }
    } catch (Exception e) {
      // Fail open if Redis/Valkey is unreachable — do not block legitimate traffic because of
      // an infrastructure issue. The full exception is logged so ops can diagnose the outage.
      log.warn("Rate limit check skipped due to cache backend failure", e);
    }

    chain.doFilter(req, res);
  }

  /**
   * Resolves the client IP, honoring {@code X-Forwarded-For} only when the request comes through a
   * trusted proxy (e.g. Nginx, Load Balancer).
   */
  private String resolveClientIp(HttpServletRequest req) {
    String remoteAddr = req.getRemoteAddr();

    // If the immediate neighbor is not a trusted proxy, do not trust any forwarded headers.
    if (!isTrustedProxy(remoteAddr)) {
      return remoteAddr == null ? "unknown" : remoteAddr;
    }

    String forwarded = req.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      int comma = forwarded.indexOf(',');
      String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
      if (!first.isEmpty()) {
        return first;
      }
    }
    String real = req.getHeader("X-Real-IP");
    if (real != null && !real.isBlank()) {
      return real.trim();
    }
    return remoteAddr == null ? "unknown" : remoteAddr;
  }

  /** Checks if the given IP address matches any of the configured trusted proxy ranges (CIDR). */
  private boolean isTrustedProxy(String ip) {
    if (ip == null) {
      return false;
    }
    return trustedProxyMatchers.stream().anyMatch(matcher -> matcher.matches(ip));
  }

  /**
   * Returns the request path with any servlet/context prefix stripped, so that prefix matching
   * works regardless of how the app is deployed (root or under a context path).
   */
  private String normalizedPath(HttpServletRequest req) {
    String servletPath = req.getServletPath();
    if (servletPath != null && !servletPath.isEmpty()) {
      return servletPath;
    }
    String uri = req.getRequestURI();
    if (uri == null) {
      return "";
    }
    String ctx = req.getContextPath();
    if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
      return uri.substring(ctx.length());
    }
    return uri;
  }

  /** {@code true} iff {@code path} equals {@code prefix} or starts with {@code prefix + '/'}. */
  private boolean matchesPrefix(String path, String prefix) {
    if (path.equals(prefix)) {
      return true;
    }
    return path.startsWith(prefix + "/");
  }

  /**
   * Throws {@link IllegalArgumentException} if {@code value} is not positive. The message quotes
   * the {@code configKey} verbatim so operators can grep their {@code application.yml}.
   */
  private static void requirePositive(String configKey, int value) {
    if (value <= 0) {
      throw new IllegalArgumentException(String.format(CONFIG_POSITIVE_MESSAGE, configKey, value));
    }
  }

  private boolean isAuthEndpoint(String path) {
    for (String prefix : AUTH_PREFIXES) {
      if (matchesPrefix(path, prefix)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Extracts the tenant id from a bearer token, or {@code null} if the token is absent, malformed,
   * or does not carry a tenant claim. Invalid tokens fall through to the public tier.
   */
  private String resolveTenantId(HttpServletRequest req) {
    String authHeader = req.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    String token = authHeader.substring("Bearer ".length());
    try {
      Long id = jwtUtil.extractTenantId(Objects.requireNonNull(token));
      return id == null ? null : id.toString();
    } catch (Exception e) {
      return null;
    }
  }
}
