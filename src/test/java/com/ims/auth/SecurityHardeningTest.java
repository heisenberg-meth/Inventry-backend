package com.ims.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

public class SecurityHardeningTest extends BaseIntegrationTest {

  @Autowired
  private SignupService signupService;
  @Autowired
  private AuthService authService;
  @Autowired
  private TwoFactorAuthService twoFactorAuthService;

  private String uniqueId;
  private String email;
  private SignupResponse signupResponse;

  @Override
  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    cleanupDatabase();
    uniqueId = UUID.randomUUID().toString().substring(0, 8);
    email = "security-test-" + uniqueId + "@example.com";
    SignupRequest req = new SignupRequest();
    req.setBusinessName("Security Test Co " + uniqueId);
    req.setBusinessType("RETAIL");
    req.setWorkspaceSlug("sec-" + uniqueId);
    req.setOwnerName("Security Admin");
    req.setOwnerEmail(email);
    req.setPassword("securePassword123");
    signupResponse = signupService.signup(req);
    verifyUserEmail(email);
  }

  @Test
  @DisplayName("Account should be temporarily locked after 5 failed login attempts")
  void testAccountLockoutAfterMaxFailedAttempts() {
    LoginRequest req = new LoginRequest();
    req.setEmail(email);
    req.setPassword("WRONG_PASSWORD");
    req.setCompanyCode(signupResponse.getCompanyCode());

    for (int i = 0; i < 4; i++) {
      assertThatThrownBy(() -> authService.login(req))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid email or password");
    }

    User user = userRepository.findByEmailGlobal(email).orElseThrow();
    assertThat(user.getFailedAttempts()).isEqualTo(4);

    assertThatThrownBy(() -> authService.login(req))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("locked");

    user = userRepository.findByEmailGlobal(email).orElseThrow();
    assertThat(user.getFailedAttempts()).isEqualTo(5);
    assertThat(user.getLockoutUntil()).isNotNull();
  }

  @Test
  @DisplayName("Successful login should clear the failed attempt counter")
  void testSuccessfulLoginClearsLockoutCounter() {
    User user = userRepository.findByEmailGlobal(email).orElseThrow();
    userRepository.recordFailedAttempt(user.getId(), 1, null);
    userRepository.recordFailedAttempt(user.getId(), 1, null);
    userRepository.recordFailedAttempt(user.getId(), 1, null);

    LoginRequest req = new LoginRequest();
    req.setEmail(email);
    req.setPassword("securePassword123");
    req.setCompanyCode(signupResponse.getCompanyCode());

    var response = authService.login(req);
    assertThat(response).isNotNull();

    user = userRepository.findByEmailGlobal(email).orElseThrow();
    assertThat(user.getFailedAttempts()).isEqualTo(0);
    assertThat(user.getLockoutUntil()).isNull();
  }

  @Test
  @DisplayName("2FA secret generation should return a valid secret and OTP auth URL")
  void testTwoFactorSecretGeneration() {
    TwoFactorAuthService.TwoFactorSecret secret = twoFactorAuthService.generateNewSecret(email);
    assertThat(secret).isNotNull();
    assertThat(secret.secret()).isNotBlank();
    assertThat(secret.qrCodeUrl()).contains("otpauth");
  }

  @Test
  @DisplayName("2FA backup codes should generate 10 unique codes")
  void testBackupCodeGeneration() {
    var codes = twoFactorAuthService.generateBackupCodes();
    assertThat(codes).hasSize(10);
    assertThat(codes.stream().distinct().count()).isEqualTo(10);
  }

  @Test
  @DisplayName("Protected endpoints should reject requests without a JWT")
  void testProtectedEndpointRequiresAuth() throws Exception {
    mockMvc.perform(post("/api/v1/tenant/orders/purchase").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("MFA-required login should return mfa_required flag")
  void testMfaRequiredLoginFlowReturnsSessionToken() {
    User user = userRepository.findByEmailGlobal(email).orElseThrow();
    userRepository.updateTwoFactorSettings(user.getId(), "JBSWY3DPEHPK3PXP", true);

    LoginRequest req = new LoginRequest();
    req.setEmail(email);
    req.setPassword("securePassword123");
    req.setCompanyCode(signupResponse.getCompanyCode());

    var response = authService.login(req);
    assertThat(response.isMfaRequired()).isTrue();
    assertThat(response.getMfaToken()).isNotBlank();
  }
}
