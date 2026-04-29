package com.ims.shared.auth;

import com.ims.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.lang.Nullable;
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
    if (isLikelyHexString(secret)) {
      keyBytes = hexStringToByteArray(secret);
    } else {
      keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    }
    this.key = Keys.hmacShaKeyFor(keyBytes);
    this.expirySeconds = expirySeconds;
    this.refreshExpirySeconds = refreshExpirySeconds;
  }

  private boolean isLikelyHexString(String s) {
    return s != null && s.length() >= 64 && s.matches("^[0-9a-fA-F]+$");
  }

  public String generateToken(
      Long userId,
      Long tenantId,
      UserRole role,
      String scope,
      String businessType,
      boolean isPlatformUser,
      Collection<String> permissions) {
    return generateToken(
        userId,
        tenantId,
        role,
        scope,
        businessType,
        isPlatformUser,
        permissions,
        false,
        null,
        null,
        expirySeconds);
  }

  public String generateToken(
      Long userId,
      Long tenantId,
      UserRole role,
      String scope,
      String businessType,
      boolean isPlatformUser,
      Collection<String> permissions,
      boolean impersonation,
      @Nullable Long impersonatedBy,
      @Nullable String sessionId,
      long customExpirySeconds) {
    Objects.requireNonNull(userId, "user id required");
    Objects.requireNonNull(scope, "scope required");
    Map<String, Object> claims = new HashMap<>();
    claims.put("user_id", userId);
    claims.put("role", role != null ? role.name() : null);
    claims.put("scope", scope);
    claims.put("is_platform_user", isPlatformUser);
    claims.put("impersonation", impersonation);
    if (impersonatedBy != null) {
      claims.put("impersonated_by", impersonatedBy);
    }
    if (sessionId != null) {
      claims.put("sid", sessionId);
    }
    if (tenantId != null) {
      claims.put("tenant_id", tenantId);
    }
    if (businessType != null) {
      claims.put("business_type", businessType);
    }
    if (permissions != null) {
      claims.put("permissions", List.copyOf(permissions));
    }

    String token = Objects.requireNonNull(
        Jwts.builder()
            .claims(claims)
            .subject(userId.toString())
            .issuedAt(new Date())
            .expiration(
                new Date(System.currentTimeMillis() + customExpirySeconds * MILLIS_IN_SECOND))
            .signWith(key)
            .compact());
    return token;
  }

  public String generateRefreshToken(
      Long userId,
      Long tenantId,
      UserRole role,
      String scope,
      String businessType,
      boolean isPlatformUser,
      Collection<String> permissions) {
    return generateRefreshToken(
        userId,
        tenantId,
        role,
        scope,
        businessType,
        isPlatformUser,
        permissions,
        false,
        null,
        null,
        refreshExpirySeconds);
  }

  public String generateRefreshToken(
      Long userId,
      Long tenantId,
      UserRole role,
      String scope,
      String businessType,
      boolean isPlatformUser,
      Collection<String> permissions,
      boolean impersonation,
      @Nullable Long impersonatedBy,
      @Nullable String sessionId,
      long customExpirySeconds) {
    Objects.requireNonNull(userId, "user id required");
    Objects.requireNonNull(scope, "scope required");
    Map<String, Object> claims = new HashMap<>();
    claims.put("user_id", userId);
    claims.put("role", role != null ? role.name() : null);
    claims.put("scope", scope);
    claims.put("is_platform_user", isPlatformUser);
    claims.put("token_type", "refresh");
    claims.put("impersonation", impersonation);
    if (impersonatedBy != null) {
      claims.put("impersonated_by", impersonatedBy);
    }
    if (sessionId != null) {
      claims.put("sid", sessionId);
    }
    if (tenantId != null) {
      claims.put("tenant_id", tenantId);
    }
    if (businessType != null) {
      claims.put("business_type", businessType);
    }
    if (permissions != null) {
      claims.put("permissions", List.copyOf(permissions));
    }

    return Objects.requireNonNull(
        Jwts.builder()
            .claims(claims)
            .subject(userId.toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + customExpirySeconds * MILLIS_IN_SECOND))
            .signWith(key)
            .compact());
  }

  public boolean validateToken(@Nullable String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    try {
      Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  public Claims extractAllClaims(String token) {
    Objects.requireNonNull(token, "token required");
    Claims tmpClaims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    return Objects.requireNonNull(tmpClaims);
  }

  public Date extractIssuedAt(String token) {
    return Objects.requireNonNull(extractAllClaims(Objects.requireNonNull(token)).getIssuedAt());
  }

  public Long extractUserId(String token) {
    return Objects.requireNonNull(extractAllClaims(Objects.requireNonNull(token)).get("user_id", Long.class));
  }

  public Long extractTenantId(String token) {
    return Objects.requireNonNull(extractAllClaims(Objects.requireNonNull(token)).get("tenant_id", Long.class));
  }

  public String extractRole(String token) {
    return Objects.requireNonNull(extractAllClaims(Objects.requireNonNull(token)).get("role", String.class));
  }

  public String extractScope(String token) {
    return Objects.requireNonNull(extractAllClaims(Objects.requireNonNull(token)).get("scope", String.class));
  }

  public String extractBusinessType(String token) {
    return Objects.requireNonNull(extractAllClaims(Objects.requireNonNull(token)).get("business_type", String.class));
  }

  public boolean extractIsPlatformUser(String token) {
    Boolean isPlatformUser = extractAllClaims(Objects.requireNonNull(token)).get("is_platform_user", Boolean.class);
    return isPlatformUser != null && isPlatformUser;
  }

  @SuppressWarnings("unchecked")
  public Set<String> extractPermissions(String token) {
    List<String> perms = (List<String>) extractAllClaims(Objects.requireNonNull(token)).get("permissions", List.class);
    return Objects.requireNonNull(perms != null ? new HashSet<>(perms) : Collections.emptySet());
  }

  public boolean extractImpersonation(String token) {
    Boolean val = extractAllClaims(Objects.requireNonNull(token)).get("impersonation", Boolean.class);
    return val != null && val;
  }

  public @Nullable Long extractImpersonatedBy(String token) {
    return extractAllClaims(Objects.requireNonNull(token)).get("impersonated_by", Long.class);
  }

  public @Nullable String extractSessionId(String token) {
    return extractAllClaims(Objects.requireNonNull(token)).get("sid", String.class);
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
      data[i / 2] = (byte) ((Character.digit(s.charAt(i), HEX_RADIX) << BYTE_SHIFT)
          + Character.digit(s.charAt(i + 1), HEX_RADIX));
    }
    return data;
  }
}
