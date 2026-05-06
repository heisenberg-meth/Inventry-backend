package com.ims.shared.ratelimit;

import com.ims.shared.auth.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sliding-window rate limiter backed by Redis (Valkey).
 *
 * <p>
 * Three tiers are enforced:
 *
 * <ul>
 * <li><b>Auth</b> — strict per-IP limit for {@code /auth/**} and
 * {@code /api/auth/**} endpoints
 * to mitigate credential stuffing and brute-force attacks.
 * <li><b>Tenant</b> — generous per-tenant+IP limit for authenticated API
 * traffic.
 * <li><b>Public</b> — per-IP limit for any other unauthenticated traffic.
 * </ul>
 *
 * <p>
 * Limits are configured via {@code app.rate-limit.*} properties in
 * {@code application.yml}. If
 * Redis is unavailable the filter fails open (logs a warning and allows the
 * request) so that a
 * cache outage does not take down the API.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

  private static final int STATUS_TOO_MANY_REQUESTS = 429;

  private static final List<String> EXCLUDED_PREFIXES = List.of(
      "/actuator",
      "/swagger-ui",
      "/v3/api-docs",
      "/api-docs",
      "/swagger-resources",
      "/webjars",
      "/favicon.ico",
      "/error");

  private static final List<String> AUTH_PREFIXES = List.of("/auth", "/api/auth");

  private final RateLimiterService rateLimiterService;
  private final JwtUtil jwtUtil;
  private final int authRpm;
  private final int publicRpm;
  private final int authenticatedRpm;
  private final int tenantRpm;
  private final int windowSeconds;

  public RateLimitFilter(
      RateLimiterService rateLimiterService,
      JwtUtil jwtUtil,
      @Value("${app.rate-limit.auth-rpm:20}") int authRpm,
      @Value("${app.rate-limit.public-rpm:50}") int publicRpm,
      @Value("${app.rate-limit.authenticated-rpm:200}") int authenticatedRpm,
      @Value("${app.rate-limit.tenant-rpm:1000}") int tenantRpm,
      @Value("${app.rate-limit.window-seconds:60}") int windowSeconds) {
    if (authRpm < 1) {
      throw new IllegalArgumentException("app.rate-limit.auth-rpm must be >= 1 (got " + authRpm + ")");
    }
    if (publicRpm < 1) {
      throw new IllegalArgumentException("app.rate-limit.public-rpm must be >= 1 (got " + publicRpm + ")");
    }
    if (authenticatedRpm < 1) {
      throw new IllegalArgumentException(
          "app.rate-limit.authenticated-rpm must be >= 1 (got " + authenticatedRpm + ")");
    }
    if (tenantRpm < 1) {
      throw new IllegalArgumentException("app.rate-limit.tenant-rpm must be >= 1 (got " + tenantRpm + ")");
    }
    if (windowSeconds < 1) {
      throw new IllegalArgumentException("app.rate-limit.window-seconds must be >= 1 (got " + windowSeconds + ")");
    }
    this.rateLimiterService = rateLimiterService;
    this.jwtUtil = jwtUtil;
    this.authRpm = authRpm;
    this.publicRpm = publicRpm;
    this.authenticatedRpm = authenticatedRpm;
    this.tenantRpm = tenantRpm;
    this.windowSeconds = windowSeconds;
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

    String path = normalizedPath(req);
    String clientIp = resolveClientIp(req);
    Long userId = resolveUserId(req);
    Long tenantId = resolveTenantId(req);

    boolean isAuthEndpoint = isAuthEndpoint(path);

    int limit;
    String key;
    String tier;

    if (isAuthEndpoint) {
      limit = authRpm;
      tier = "auth";
      key = "rate_limit:ip:" + clientIp;
    } else if (userId != null) {
      limit = authenticatedRpm;
      tier = "user";
      key = "rate_limit:user:" + userId;
    } else {
      limit = publicRpm;
      tier = "public";
      key = "rate_limit:ip:" + clientIp;
    }

    // 1. Check Primary Limit (User or IP)
    if (!rateLimiterService.isAllowed(key, limit, windowSeconds)) {
      handleRateLimitExceeded(res, tier, key, limit);
      return;
    }

    // 2. Optional: Check Tenant-wide Limit
    if (tenantId != null) {
      String tenantKey = "rate_limit:tenant:" + tenantId;
      if (!rateLimiterService.isAllowed(tenantKey, tenantRpm, windowSeconds)) {
        handleRateLimitExceeded(res, "tenant", tenantKey, tenantRpm);
        return;
      }
    }

    int currentCount = rateLimiterService.getCount(key);
    res.setHeader("X-RateLimit-Limit", String.valueOf(limit));
    res.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - currentCount)));
    res.setHeader("X-RateLimit-Window-Seconds", String.valueOf(windowSeconds));

    chain.doFilter(req, res);
  }

  private void handleRateLimitExceeded(HttpServletResponse res, String tier, String key, int limit) throws IOException {
    log.warn("Rate limit exceeded (tier={}, key={}, limit={})", tier, key, limit);
    res.setStatus(STATUS_TOO_MANY_REQUESTS);
    res.setHeader("Retry-After", String.valueOf(windowSeconds));
    res.setContentType("application/json");
    res.getWriter()
        .write(
            String.format(
                "{\"status\":\"error\",\"message\":\"Rate limit exceeded\",\"retry_after\":%d}",
                windowSeconds));
  }

  private String resolveClientIp(HttpServletRequest req) {
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
    return req.getRemoteAddr() == null ? "unknown" : req.getRemoteAddr();
  }

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

  private boolean matchesPrefix(String path, String prefix) {
    if (path.equals(prefix)) {
      return true;
    }
    return path.startsWith(prefix + "/");
  }

  private boolean isAuthEndpoint(String path) {
    for (String prefix : AUTH_PREFIXES) {
      if (matchesPrefix(path, prefix)) {
        return true;
      }
    }
    return false;
  }

  private Long resolveUserId(HttpServletRequest req) {
    String authHeader = req.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    String token = authHeader.substring("Bearer ".length());
    try {
      return jwtUtil.extractUserId(token);
    } catch (Exception e) {
      return null;
    }
  }

  private Long resolveTenantId(HttpServletRequest req) {
    String authHeader = req.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    String token = authHeader.substring("Bearer ".length());
    try {
      return jwtUtil.extractTenantId(token);
    } catch (Exception e) {
      return null;
    }
  }
}
