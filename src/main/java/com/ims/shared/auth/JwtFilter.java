package com.ims.shared.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;
  private final RedisTemplate<String, Object> redisTemplate;

  private static final int BEARER_PREFIX_LENGTH = 7;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {
    String authHeader = request.getHeader("Authorization");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      chain.doFilter(request, response);
      return;
    }

    String token = Objects.requireNonNull(authHeader.substring(BEARER_PREFIX_LENGTH));

    try {
      if (!jwtUtil.validateToken(token)) {
        chain.doFilter(request, response);
        return;
      }

      // Check JWT blacklist
      String tokenHash = hashToken(token);
      boolean redisAvailable = true;
      Boolean isBlacklisted = false;
      try {
        String safeTokenHash = Objects.requireNonNull(tokenHash);
        isBlacklisted = redisTemplate.hasKey("jwt:blacklist:" + safeTokenHash);
      } catch (Exception e) {
        log.warn("Redis unavailable for JWT blacklist check — rejecting request to prevent bypass");
        redisAvailable = false;
      }

      if (!redisAvailable || Boolean.TRUE.equals(isBlacklisted)) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        String errorMessage = !redisAvailable ? "Service temporarily unavailable" : "Token has been revoked";
        response
            .getWriter()
            .write(
                String.format(
                    "{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"%s\"}",
                    errorMessage));
        return;
      }

      String safeToken = Objects.requireNonNull(token);
      final Long userId = jwtUtil.extractUserId(safeToken);
      final Long tenantId = Objects.requireNonNull(
          jwtUtil.extractTenantId(safeToken),
          "tenantId missing in JWT");
      final boolean isPlatformUser = jwtUtil.extractIsPlatformUser(safeToken);

      TenantContext.setTenantId(tenantId);

      MDC.put("tenantId", tenantId != null ? String.valueOf(tenantId) : "none");
      MDC.put("userId", userId != null ? String.valueOf(userId) : "anonymous");
      if (MDC.get("requestId") == null) {
        MDC.put("requestId", UUID.randomUUID().toString());
      }

      final String role = Objects.requireNonNull(jwtUtil.extractRole(safeToken));
      final String scope = Objects.requireNonNull(jwtUtil.extractScope(safeToken));
      final String businessType = jwtUtil.extractBusinessType(safeToken);
      final Set<String> permissions = Objects.requireNonNull(jwtUtil.extractPermissions(safeToken));
      final boolean impersonation = jwtUtil.extractImpersonation(safeToken);
      final Long impersonatedBy = jwtUtil.extractImpersonatedBy(safeToken);

      // Enforce impersonation session validity
      if (impersonation) {
        String sessionId = jwtUtil.extractSessionId(safeToken);
        if (sessionId == null || !redisTemplate.hasKey("impersonation:session:" + sessionId)) {
          log.warn("Impersonation session {} is invalid or revoked", sessionId);
          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          response.setContentType("application/json");
          response.getWriter().write("{\"error\":\"Impersonation session expired or revoked\"}");
          return;
        }
      }

      String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
      UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
          Objects.requireNonNull(userId),
          null,
          List.of(new SimpleGrantedAuthority(authority)));

      // Store additional details for downstream use
      auth.setDetails(
          new JwtAuthDetails(
              userId,
              tenantId,
              role,
              scope,
              businessType,
              isPlatformUser,
              permissions,
              impersonation,
              impersonatedBy));
      SecurityContextHolder.getContext().setAuthentication(auth);

      chain.doFilter(request, response);
    } finally {
      TenantContext.clear(); // CRITICAL — prevents tenant bleed between requests
      MDC.clear(); // CRITICAL — avoids MDC leakage across threads
    }
  }

  private String hashToken(String token) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(token.getBytes());
      return Objects.requireNonNull(HexFormat.of().formatHex(hash));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    String path = request.getRequestURI();
    // Only bypass for health check. Other actuator/swagger paths need auth in prod.
    return "/actuator/health".equals(path)
        || path.equals("/api/v1/actuator/health")
        || path.contains("/auth/")
        || path.contains("/platform/auth/");
  }
}
