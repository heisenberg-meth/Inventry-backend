package com.ims.shared.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;
  private final RedisTemplate<String, Object> redisTemplate;

  private static final int BEARER_PREFIX_LENGTH = 7;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
      throws ServletException, IOException {
    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      chain.doFilter(request, response);
      return;
    }

    String token = authHeader.substring(BEARER_PREFIX_LENGTH);

    try {
      if (!jwtUtil.validateToken(token)) {
        chain.doFilter(request, response);
        return;
      }

      // Check JWT blacklist
      String tokenHash = hashToken(token);
      Boolean isBlacklisted = false;
      try {
        isBlacklisted = redisTemplate.hasKey("jwt:blacklist:" + tokenHash);
      } catch (Exception e) {
        logger.warn("Redis unavailable for JWT blacklist check: " + e.getMessage());
      }

      if (Boolean.TRUE.equals(isBlacklisted)) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response
            .getWriter()
            .write(
                "{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Token has been revoked\"}");
        return;
      }

      Claims claims = jwtUtil.extractAllClaims(token);
      Long userId = claims.get("user_id", Long.class);
      Long tenantId = claims.get("tenant_id", Long.class);
      String role = claims.get("role", String.class);
      String scope = claims.get("scope", String.class);
      String businessType = claims.get("business_type", String.class);
      boolean isPlatformUser = Boolean.TRUE.equals(claims.get("is_platform_user", Boolean.class));

      // 🔑 Normalize to ROLE_ prefix for Spring Security
      var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

      var auth = new JwtAuthenticationToken(
          String.valueOf(userId),
          userId,
          tenantId,
          authorities);

      // Set details for backward compatibility with existing code
      auth.setDetails(new JwtAuthDetails(userId, tenantId, role, scope, businessType, isPlatformUser));

      SecurityContextHolder.getContext().setAuthentication(auth);

      if (tenantId != null) {
        TenantContext.setTenantId(tenantId);
        MDC.put("tenantId", String.valueOf(tenantId));
      }

      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      MDC.remove("tenantId");
      // SecurityContext is typically cleared by the framework, but manual clearing is
      // safer
      // if we aren't using the standard SecurityContextPersistenceFilter correctly
    }
  }

  private String hashToken(String token) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(token.getBytes());
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.endsWith("/auth/login")
        || path.endsWith("/auth/signup")
        || path.endsWith("/auth/forgot-password")
        || path.endsWith("/auth/reset-password")
        || path.startsWith("/actuator/")
        || path.startsWith("/swagger-ui")
        || path.startsWith("/api-docs");
  }
}
