package com.ims.shared.auth;
import com.ims.model.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  private static final long MILLIS_IN_SECOND = 1000L;
  private static final int HEX_RADIX = 16;
  private static final int BYTE_SHIFT = 4;


  private final SecretKey key;
  private final long expirySeconds;
  private final long refreshExpirySeconds;

  public JwtUtil(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiry-seconds}") long expirySeconds,
      @Value("${app.jwt.refresh-expiry-seconds}") long refreshExpirySeconds) {
    byte[] keyBytes;
    if (isHexString(secret)) {
      keyBytes = hexStringToByteArray(secret);
    } else {
      keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    this.key = Keys.hmacShaKeyFor(keyBytes);
    this.expirySeconds = expirySeconds;
    this.refreshExpirySeconds = refreshExpirySeconds;
  }

  private boolean isHexString(String s) {
    return s != null && s.length() % 2 == 0 && s.matches("^[0-9a-fA-F]+$");
  }

  public @NonNull String generateToken(
      @NonNull Long userId, @Nullable Long tenantId, @Nullable UserRole role, @NonNull String scope, @Nullable String businessType, boolean isPlatformUser, @Nullable java.util.Collection<String> permissions) {
    return generateToken(userId, tenantId, role, scope, businessType, isPlatformUser, permissions, false, null, expirySeconds);
  }

  public @NonNull String generateToken(
      @NonNull Long userId, @Nullable Long tenantId, @Nullable UserRole role, @NonNull String scope, @Nullable String businessType, boolean isPlatformUser, @Nullable java.util.Collection<String> permissions,
      boolean impersonation, @Nullable Long impersonatedBy, long customExpirySeconds) {
    Objects.requireNonNull(userId, "user id required");
    Objects.requireNonNull(scope, "scope required");
    Map<String, Object> claims = new HashMap<>();
    claims.put("user_id", userId);
    claims.put("role", role != null ? role.name() : null);
    claims.put("scope", scope);
    claims.put("is_platform_user", isPlatformUser);
    claims.put("permissions", permissions);
    claims.put("impersonation", impersonation);
    if (impersonatedBy != null) {
      claims.put("impersonated_by", impersonatedBy);
    }
    if (tenantId != null) {
      claims.put("tenant_id", tenantId);
    }
    if (businessType != null) {
      claims.put("business_type", businessType);
    }

    return Jwts.builder()
        .claims(claims)
        .subject(userId.toString())
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + customExpirySeconds * MILLIS_IN_SECOND))
        .signWith(key)
        .compact();
  }

  public @NonNull String generateRefreshToken(
      @NonNull Long userId, @Nullable Long tenantId, @Nullable UserRole role, @NonNull String scope, @Nullable String businessType, boolean isPlatformUser, @Nullable java.util.Collection<String> permissions) {
    return generateRefreshToken(userId, tenantId, role, scope, businessType, isPlatformUser, permissions, false, null, refreshExpirySeconds);
  }

  public @NonNull String generateRefreshToken(
      @NonNull Long userId, @Nullable Long tenantId, @Nullable UserRole role, @NonNull String scope, @Nullable String businessType, boolean isPlatformUser, @Nullable java.util.Collection<String> permissions,
      boolean impersonation, @Nullable Long impersonatedBy, long customExpirySeconds) {
    Objects.requireNonNull(userId, "user id required");
    Objects.requireNonNull(scope, "scope required");
    Map<String, Object> claims = new HashMap<>();
    claims.put("user_id", userId);
    claims.put("role", role != null ? role.name() : null);
    claims.put("scope", scope);
    claims.put("is_platform_user", isPlatformUser);
    claims.put("token_type", "refresh");
    claims.put("permissions", permissions);
    claims.put("impersonation", impersonation);
    if (impersonatedBy != null) {
      claims.put("impersonated_by", impersonatedBy);
    }
    if (tenantId != null) {
      claims.put("tenant_id", tenantId);
    }
    if (businessType != null) {
      claims.put("business_type", businessType);
    }

    return Jwts.builder()
        .claims(claims)
        .subject(userId.toString())
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + customExpirySeconds * MILLIS_IN_SECOND))
        .signWith(key)
        .compact();
  }

  public boolean validateToken(@Nullable String token) {
    if (token == null || token.isBlank()) return false;
    try {
      Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  public @NonNull Claims extractAllClaims(@NonNull String token) {
    Objects.requireNonNull(token, "token required");
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  public Long extractUserId(String token) {
    return extractAllClaims(token).get("user_id", Long.class);
  }

  public Long extractTenantId(String token) {
    return extractAllClaims(token).get("tenant_id", Long.class);
  }

  public String extractRole(String token) {
    return extractAllClaims(token).get("role", String.class);
  }

  public String extractScope(String token) {
    return extractAllClaims(token).get("scope", String.class);
  }

  public String extractBusinessType(String token) {
    return extractAllClaims(token).get("business_type", String.class);
  }

  public boolean extractIsPlatformUser(String token) {
    Boolean isPlatformUser = extractAllClaims(token).get("is_platform_user", Boolean.class);
    return isPlatformUser != null && isPlatformUser;
  }

  public java.util.Set<String> extractPermissions(String token) {
    @SuppressWarnings("unchecked")
    java.util.List<String> perms = (java.util.List<String>) extractAllClaims(token).get("permissions", java.util.List.class);
    return perms != null ? new java.util.HashSet<>(perms) : java.util.Collections.emptySet();
  }

  public boolean extractImpersonation(String token) {
    Boolean val = extractAllClaims(token).get("impersonation", Boolean.class);
    return val != null && val;
  }

  public Long extractImpersonatedBy(String token) {
    return extractAllClaims(token).get("impersonated_by", Long.class);
  }

  public long getExpirySeconds() {
    return expirySeconds;
  }

  public long getRefreshExpirySeconds() {
    return refreshExpirySeconds;
  }

  private byte[] hexStringToByteArray(String s) {
    int len = s.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] =
          (byte)
              ((Character.digit(s.charAt(i), HEX_RADIX) << BYTE_SHIFT)
                  + Character.digit(s.charAt(i + 1), HEX_RADIX));
    }
    return data;
  }
}
