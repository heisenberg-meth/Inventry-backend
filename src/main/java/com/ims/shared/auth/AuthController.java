package com.ims.shared.auth;

import com.ims.dto.request.ChangePasswordRequest;
import com.ims.dto.request.ForgotPasswordRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.MfaRequest;
import com.ims.dto.request.ResetPasswordRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.shared.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Login, Logout, Token Refresh, Password Management")
public class AuthController {

  private final AuthService authService;

  private static final int BEARER_PREFIX_LENGTH = 7;

  @PostMapping("/login")
  @Operation(summary = "Login", description = "Authenticate with email/password, returns JWT tokens")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/logout")
  @Operation(summary = "Logout", description = "Blacklists the JWT token in Redis")
  public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = Objects.requireNonNull(authHeader.substring(BEARER_PREFIX_LENGTH));
      authService.logout(token);
    }
    return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
  }

  @PostMapping("/impersonation/end")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "End Impersonation", description = "Terminates an active impersonation session")
  public ResponseEntity<Map<String, String>> endImpersonation(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = Objects.requireNonNull(authHeader.substring(BEARER_PREFIX_LENGTH));
      authService.endImpersonation(token);
    }
    return ResponseEntity.ok(Map.of("message", "Impersonation session ended"));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Refresh Token", description = "Exchange refresh token for new access token")
  public ResponseEntity<LoginResponse> refresh(@RequestBody Map<String, String> body) {
    String refreshToken = body.get("refresh_token");
    if (refreshToken == null || refreshToken.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    String safeToken = Objects.requireNonNull(refreshToken);
    LoginResponse response = authService.refresh(safeToken);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/me")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Get current user profile")
  public ResponseEntity<Map<String, Object>> getProfile() {
    Long userId = Objects.requireNonNull(extractUserId());
    Map<String, Object> profile = authService.getProfile(userId);
    return ResponseEntity.ok(profile);
  }

  @PatchMapping("/change-password")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Change password", description = "Requires current password")
  public ResponseEntity<Map<String, String>> changePassword(
      @Valid @RequestBody ChangePasswordRequest request) {
    Long userId = Objects.requireNonNull(extractUserId());
    Map<String, String> response = authService.changePassword(userId, Objects.requireNonNull(request));
    return ResponseEntity.ok(response);
  }

  @PostMapping("/forgot-password")
  @Operation(summary = "Forgot password", description = "Request password reset token (sent via email)")
  public ResponseEntity<Map<String, String>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request) {
    Map<String, String> response = authService.forgotPassword(Objects.requireNonNull(request));
    return ResponseEntity.ok(response);
  }

  @PostMapping("/reset-password")
  @Operation(summary = "Reset password", description = "Reset password using reset token")
  public ResponseEntity<Map<String, String>> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request) {
    Map<String, String> response = authService.resetPassword(Objects.requireNonNull(request));
    return ResponseEntity.ok(response);
  }

  @GetMapping("/verify-email")
  @Operation(summary = "Verify email", description = "Verify user email using verification token")
  public ResponseEntity<Map<String, String>> verifyEmail(
      @RequestParam String token, @RequestParam String email) {
    Map<String, String> response = authService.verifyEmail(
            Objects.requireNonNull(token), Objects.requireNonNull(email));
    return ResponseEntity.ok(response);
  }

  @PostMapping("/resend-verification")
  @Operation(summary = "Resend verification email", description = "Resend email verification token")
  public ResponseEntity<Map<String, String>> resendVerification(
      @RequestBody Map<String, String> body) {
    String email = body.get("email");
    if (email == null || email.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    Map<String, String> response = authService.resendVerification(Objects.requireNonNull(email));
    return ResponseEntity.ok(response);
  }

  @GetMapping("/check-email")
  @Operation(summary = "Check email availability")
  public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestParam String email) {
    Map<String, Boolean> response = authService.checkEmail(Objects.requireNonNull(email));
    return ResponseEntity.ok(response);
  }

  @GetMapping("/check-slug")
  @Operation(summary = "Check workspace slug availability")
  public ResponseEntity<Map<String, Boolean>> checkSlug(@RequestParam String slug) {
    Map<String, Boolean> response = authService.checkSlug(Objects.requireNonNull(slug));
    return ResponseEntity.ok(response);
  }

  @GetMapping("/check-company-code")
  @Operation(summary = "Check if company code exists")
  public ResponseEntity<Map<String, Boolean>> checkCompanyCode(@RequestParam String code) {
    Map<String, Boolean> response = authService.checkCompanyCode(Objects.requireNonNull(code));
    return ResponseEntity.ok(response);
  }

  @GetMapping("/permissions")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Get current user permissions")
  public ResponseEntity<Map<String, Object>> getMyPermissions() {
    Long userId = Objects.requireNonNull(extractUserId());
    Map<String, Object> response = authService.getMyPermissions(userId);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/validate")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Validate current token")
  public ResponseEntity<Map<String, Boolean>> validateToken() {
    Map<String, Boolean> response = Map.of("valid", true);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/2fa/setup")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Initiate 2FA setup", description = "Generates TOTP secret and QR code URL")
  public ResponseEntity<Map<String, Object>> setup2fa() {
    Long userId = Objects.requireNonNull(extractUserId());
    Map<String, Object> response = authService.setup2FA(userId);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/2fa/enable")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Complete 2FA setup", description = "Verifies initial code and enables 2FA")
  public ResponseEntity<Map<String, Object>> enable2fa(@RequestBody Map<String, String> body) {
    Long userId = Objects.requireNonNull(extractUserId());
    String code = Objects.requireNonNull(body.get("code"), "Code is required");
    Map<String, Object> response = authService.enable2FA(userId, code);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/2fa/disable")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Disable 2FA", description = "Requires current password")
  public ResponseEntity<Map<String, String>> disable2fa(@RequestBody Map<String, String> body) {
    Long userId = Objects.requireNonNull(extractUserId());
    String password = Objects.requireNonNull(body.get("password"), "Password is required");
    Map<String, String> response = authService.disable2FA(userId, password);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/2fa/verify")
  @Operation(summary = "Verify MFA code during login challenge")
  public ResponseEntity<LoginResponse> verifyMfa(@Valid @RequestBody MfaRequest request) {
    return ResponseEntity.ok(authService.verifyMfa(request));
  }

  private Long extractUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
      return Objects.requireNonNull(details.getUserId());
    }
    throw new UnauthorizedException("User not authenticated");
  }
}
