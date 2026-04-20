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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
      @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
      throws ServletException, IOException {
    String authHeader = request.getHeader("Authorization");

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

      Long userId = jwtUtil.extractUserId(token);
      Long tenantId = jwtUtil.extractTenantId(token);
      String role = jwtUtil.extractRole(token);
      String scope = jwtUtil.extractScope(token);
      String businessType = jwtUtil.extractBusinessType(token);
      boolean isPlatformUser = jwtUtil.extractIsPlatformUser(token);

      TenantContext.setTenantId(tenantId);
      log.info("Tenant ID from JWT: {}", tenantId);

      UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
          userId, null, List.of(new SimpleGrantedAuthority(role)));

      // Store additional details for downstream use
      auth.setDetails(new JwtAuthDetails(userId, tenantId, role, scope, businessType, isPlatformUser));
      SecurityContextHolder.getContext().setAuthentication(auth);

      chain.doFilter(request, response);
    } finally {
      TenantContext.clear(); // CRITICAL — prevents tenant bleed between requests
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
    return path.startsWith("/actuator/")
        || path.startsWith("/swagger-ui")
        || path.startsWith("/api-docs")
        || path.startsWith("/v3/api-docs");
  }
}
