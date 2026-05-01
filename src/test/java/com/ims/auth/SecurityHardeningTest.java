package com.ims.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.SignupResponse;
import com.ims.model.User;
import com.ims.shared.auth.AuthService;
import com.ims.shared.auth.SignupService;
import com.ims.shared.auth.TwoFactorAuthService;
import com.ims.shared.exception.UnauthorizedException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;

/**
 * Integration tests covering the security hardening from Chunk 2 & 5:
 * - Account lockout after repeated failures
 * - 2FA (TOTP) setup and backup codes
 * - MFA challenge-response login flow
 */
public class SecurityHardeningTest extends BaseIntegrationTest {

  @Autowired
  private SignupService signupService;
  @Autowired
  private AuthService authService;
  @Autowired
  private TwoFactorAuthService twoFactorAuthService;

  @Autowired
  private ValueOperations<String, Object> valueOperations;

  private String uniqueId;
  private String email;
  private SignupResponse signupResponse;

  @BeforeEach
  void setup() {
    cleanupDatabase();
    mockRedisAndCache();
    uniqueId = UUID.randomUUID().toString().substring(0, 8);
    email = "security-test-" + uniqueId + "@example.com";
    SignupRequest req = new SignupRequest();
    req.setBusinessName("Security Test Co " + uniqueId);
    req.setBusinessType("Retail");
    req.setWorkspaceSlug("sec-" + uniqueId);
    req.setOwnerName("Security Admin");
    req.setOwnerEmail(email);
    req.setPassword("securePassword123");
    signupResponse = signupService.signup(req);
    verifyUserEmail(email);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 1. Account Lockout Tests
  // ────────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Account should be temporarily locked after 5 failed login attempts")
  void testAccountLockoutAfterMaxFailedAttempts() {
    LoginRequest req = new LoginRequest();
    req.setEmail(email);
    req.setPassword("WRONG_PASSWORD");
    req.setCompanyCode(signupResponse.getCompanyCode());

    // Simulate 4 failed attempts (Redis counter increments)
    // On attempt 5+, the 15-minute lockout should kick in.
    for (int i = 0; i < 4; i++) {
      String attemptNum = String.valueOf(i + 1);
      doReturn(attemptNum).when(valueOperations).get("auth:failed:" + email);
      assertThatThrownBy(() -> authService.login(req))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid email or password");
    }

    // On the 5th attempt, Redis says we've hit the threshold
    doReturn("5").when(valueOperations).get("auth:failed:" + email);
    assertThatThrownBy(() -> authService.login(req))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("temporarily locked");
  }

  @Test
  @DisplayName("Successful login should clear the failed attempt counter")
  void testSuccessfulLoginClearsLockoutCounter() {
    LoginRequest req = new LoginRequest();
    req.setEmail(email);
    req.setPassword("securePassword123");
    req.setCompanyCode(signupResponse.getCompanyCode());

    // Simulate having 2 previous failed attempts
    doReturn("2").when(valueOperations).get("auth:failed:" + email);

    // Successful login should not throw and should call delete on the counter key
    var response = authService.login(req);
    assertThat(response).isNotNull();
    // The access token should be present (or mfa token)
    assertThat(response.getAccessToken() != null || response.getMfaToken() != null).isTrue();

    // Verify Redis delete was called with the correct lockout key
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(redisTemplate, Mockito.atLeastOnce())
        .delete(keyCaptor.capture());
    assertThat(keyCaptor.getAllValues()).anyMatch(k -> k.equals("auth:failed:" + email));
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 2. 2FA / TOTP Tests
  // ────────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("2FA secret generation should return a valid secret and OTP auth URL")
  void testTwoFactorSecretGeneration() {
    TwoFactorAuthService.TwoFactorSecret secret = twoFactorAuthService.generateNewSecret(email);

    assertThat(secret).isNotNull();
    assertThat(secret.secret()).isNotBlank();
    // Use contains ignoring case or check for encoded values if needed,
    // but the most robust way is to check for the key parts.
    assertThat(secret.qrCodeUrl()).contains("otpauth");
    assertThat(secret.qrCodeUrl()).contains("totp");
    assertThat(secret.qrCodeUrl()).contains(email.replace("@", "%40"));
    assertThat(secret.qrCodeUrl()).contains("IMS-Inventory");
  }

  @Test
  @DisplayName("2FA backup codes should generate 10 unique codes of correct length")
  void testBackupCodeGeneration() {
    var codes = twoFactorAuthService.generateBackupCodes();

    assertThat(codes).hasSize(10);
    // All codes must be unique
    assertThat(codes.stream().distinct().count()).isEqualTo(10);
    // Each code should be 8 chars uppercase
    codes.forEach(code -> {
      assertThat(code).hasSize(8);
      assertThat(code).matches("[A-F0-9]+");
    });
  }

  @Test
  @DisplayName("Invalid TOTP code should return false on verification")
  void testInvalidTotpCodeReturnsFalse() {
    TwoFactorAuthService.TwoFactorSecret secret = twoFactorAuthService.generateNewSecret(email);
    // 000000 is virtually guaranteed to be wrong
    assertThat(twoFactorAuthService.verifyCode(secret.secret(), 0)).isFalse();
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 3. Unauthenticated Access Tests
  // ────────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Protected endpoints should reject requests without a JWT")
  void testProtectedEndpointRequiresAuth() throws Exception {
    mockMvc
        .perform(post("/api/v1/tenant/orders/purchase").contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("MFA-required login should return mfa_required flag with a session token")
  void testMfaRequiredLoginFlowReturnsSessionToken() {
    // Simulate a user who has 2FA enabled
    User user = userRepository.findByEmailGlobal(email).orElseThrow();
    user.setTwoFactorSecret("JBSWY3DPEHPK3PXP"); // valid base32 seed
    user.setTwoFactorEnabled(true);
    userRepository.save(user);

    LoginRequest req = new LoginRequest();
    req.setEmail(email);
    req.setPassword("securePassword123");
    req.setCompanyCode(signupResponse.getCompanyCode());

    // Mock Redis opsForValue().set() to do nothing (normal mock behavior)
    var response = authService.login(req);

    assertThat(response.isMfaRequired()).isTrue();
    assertThat(response.getMfaToken()).isNotBlank();
    assertThat(response.getAccessToken()).isNull();
  }
}
