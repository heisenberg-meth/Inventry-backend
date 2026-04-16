package com.ims.shared.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
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
    this.key = Keys.hmacShaKeyFor(hexStringToByteArray(secret));
    this.expirySeconds = expirySeconds;
    this.refreshExpirySeconds = refreshExpirySeconds;
  }

  public String generateToken(
      Long userId, Long tenantId, String role, String scope, String businessType, boolean isPlatformUser) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("user_id", userId);
    claims.put("role", role);
    claims.put("scope", scope);
    claims.put("is_platform_user", isPlatformUser);
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
        .expiration(new Date(System.currentTimeMillis() + expirySeconds * MILLIS_IN_SECOND))
        .signWith(key)
        .compact();
  }

  public String generateRefreshToken(
      Long userId, Long tenantId, String role, String scope, String businessType, boolean isPlatformUser) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("user_id", userId);
    claims.put("role", role);
    claims.put("scope", scope);
    claims.put("is_platform_user", isPlatformUser);
    claims.put("token_type", "refresh");
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
        .expiration(new Date(System.currentTimeMillis() + refreshExpirySeconds * MILLIS_IN_SECOND))
        .signWith(key)
        .compact();
  }

  public boolean validateToken(String token) {
    try {
      Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  public Claims extractAllClaims(String token) {
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

  public long getExpirySeconds() {
    return expirySeconds;
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
