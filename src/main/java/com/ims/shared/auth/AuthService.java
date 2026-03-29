package com.ims.shared.auth;

import com.ims.dto.request.LoginRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.model.Tenant;
import com.ims.model.User;
import com.ims.platform.repository.TenantRepository;
import com.ims.tenant.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

  private static final int LOGOUT_EXPIRY_HOURS = 24;
  private static final int HASH_LOG_LENGTH = 8;

  private final UserRepository userRepository;

  private final TenantRepository tenantRepository;
  private final JwtUtil jwtUtil;
  private final PasswordEncoder passwordEncoder;
  private final RedisTemplate<String, Object> redisTemplate;

  public LoginResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmailUnfiltered(request.getEmail())
            .orElseThrow(() -> new EntityNotFoundException("Invalid email or password"));

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new IllegalArgumentException("Invalid email or password");
    }

    if (!Boolean.TRUE.equals(user.getIsActive())) {
      throw new IllegalArgumentException("Account is deactivated");
    }

    String scope = user.getScope();
    String businessType = null;
    Long tenantId = user.getTenantId();

    if ("TENANT".equals(scope)) {
      if (tenantId != null) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant != null) {
          businessType = tenant.getBusinessType();
        }
      }
    }

    String accessToken =
        jwtUtil.generateToken(user.getId(), tenantId, user.getRole(), scope, businessType);
    String refreshToken =
        jwtUtil.generateRefreshToken(user.getId(), tenantId, user.getRole(), scope, businessType);

    log.info(
        "Login successful: user={} role={} tenant={}", user.getEmail(), user.getRole(), tenantId);

    return LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(jwtUtil.getExpirySeconds())
        .tenantId(tenantId)
        .role(user.getRole())
        .businessType(businessType)
        .build();
  }

  public void logout(String token) {
    String tokenHash = hashToken(token);
    redisTemplate
        .opsForValue()
        .set("jwt:blacklist:" + tokenHash, "revoked", LOGOUT_EXPIRY_HOURS, TimeUnit.HOURS);
    log.info("Token blacklisted: {}", tokenHash.substring(0, HASH_LOG_LENGTH) + "...");
  }


  public LoginResponse refresh(String refreshToken) {
    if (!jwtUtil.validateToken(refreshToken)) {
      throw new IllegalArgumentException("Invalid refresh token");
    }

    String tokenType = jwtUtil.extractAllClaims(refreshToken).get("token_type", String.class);
    if (!"refresh".equals(tokenType)) {
      throw new IllegalArgumentException("Not a refresh token");
    }

    Long userId = jwtUtil.extractUserId(refreshToken);
    User user =
        userRepository
            .findByIdUnfiltered(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    String scope = user.getScope();
    String businessType = null;
    Long tenantId = user.getTenantId();

    if ("TENANT".equals(scope)) {
      if (tenantId != null) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant != null) {
          businessType = tenant.getBusinessType();
        }
      }
    }

    String newAccessToken =
        jwtUtil.generateToken(user.getId(), tenantId, user.getRole(), scope, businessType);
    String newRefreshToken =
        jwtUtil.generateRefreshToken(user.getId(), tenantId, user.getRole(), scope, businessType);

    // Blacklist old refresh token
    logout(refreshToken);

    return LoginResponse.builder()
        .accessToken(newAccessToken)
        .refreshToken(newRefreshToken)
        .expiresIn(jwtUtil.getExpirySeconds())
        .tenantId(tenantId)
        .role(user.getRole())
        .businessType(businessType)
        .build();
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
}
