package com.ims.shared.auth;

import com.ims.dto.request.ChangePasswordRequest;
import com.ims.dto.request.ForgotPasswordRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.ResetPasswordRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.shared.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
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
  @Operation(
      summary = "Login",
      description = "Authenticate with email/password, returns JWT tokens")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    log.info(
        "Login request received: email={}, companyCode={}",
        request.getEmail(),
        request.getCompanyCode());
    LoginResponse response = authService.login(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/logout")
  @Operation(summary = "Logout", description = "Blacklists the JWT token in Redis")
  public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = java.util.Objects.requireNonNull(authHeader.substring(BEARER_PREFIX_LENGTH));
      authService.logout(token);
    }
    return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Refresh Token", description = "Exchange refresh token for new access token")
  public ResponseEntity<LoginResponse> refresh(@RequestBody Map<String, String> body) {
    String refreshToken = body.get("refresh_token");
    if (refreshToken == null || refreshToken.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    String safeToken = java.util.Objects.requireNonNull(refreshToken);
    LoginResponse response = authService.refresh(safeToken);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/me")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Get current user profile")
  public ResponseEntity<Map<String, Object>> getProfile() {
    Long userId = java.util.Objects.requireNonNull(extractUserId());
    return ResponseEntity.ok(authService.getProfile(userId));
  }

  @PatchMapping("/change-password")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Change password", description = "Requires current password")
  public ResponseEntity<Map<String, String>> changePassword(
      @Valid @RequestBody ChangePasswordRequest request) {
    Long userId = java.util.Objects.requireNonNull(extractUserId());
    return ResponseEntity.ok(
        authService.changePassword(userId, java.util.Objects.requireNonNull(request)));
  }

  @PostMapping("/forgot-password")
  @Operation(
      summary = "Forgot password",
      description = "Request password reset token (sent via email)")
  public ResponseEntity<Map<String, String>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request) {
    return ResponseEntity.ok(authService.forgotPassword(java.util.Objects.requireNonNull(request)));
  }

  @PostMapping("/reset-password")
  @Operation(summary = "Reset password", description = "Reset password using reset token")
  public ResponseEntity<Map<String, String>> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request) {
    return ResponseEntity.ok(authService.resetPassword(java.util.Objects.requireNonNull(request)));
  }

  @GetMapping("/verify-email")
  @Operation(summary = "Verify email", description = "Verify user email using verification token")
  public ResponseEntity<Map<String, String>> verifyEmail(
      @RequestParam String token, @RequestParam String email) {
    return ResponseEntity.ok(
        authService.verifyEmail(
            java.util.Objects.requireNonNull(token), java.util.Objects.requireNonNull(email)));
  }

  @PostMapping("/resend-verification")
  @Operation(summary = "Resend verification email", description = "Resend email verification token")
  public ResponseEntity<Map<String, String>> resendVerification(
      @RequestBody Map<String, String> body) {
    String email = body.get("email");
    if (email == null || email.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(
        authService.resendVerification(java.util.Objects.requireNonNull(email)));
  }

  @GetMapping("/check-email")
  @Operation(summary = "Check email availability")
  public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestParam String email) {
    return ResponseEntity.ok(authService.checkEmail(java.util.Objects.requireNonNull(email)));
  }

  @GetMapping("/check-slug")
  @Operation(summary = "Check workspace slug availability")
  public ResponseEntity<Map<String, Boolean>> checkSlug(@RequestParam String slug) {
    return ResponseEntity.ok(authService.checkSlug(java.util.Objects.requireNonNull(slug)));
  }

  @GetMapping("/check-company-code")
  @Operation(summary = "Check if company code exists")
  public ResponseEntity<Map<String, Boolean>> checkCompanyCode(@RequestParam String code) {
    return ResponseEntity.ok(authService.checkCompanyCode(java.util.Objects.requireNonNull(code)));
  }

  @GetMapping("/permissions")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Get current user permissions")
  public ResponseEntity<Map<String, Object>> getMyPermissions() {
    Long userId = java.util.Objects.requireNonNull(extractUserId());
    return ResponseEntity.ok(authService.getMyPermissions(userId));
  }

  @GetMapping("/validate")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Validate current token")
  public ResponseEntity<Map<String, Boolean>> validateToken() {
    return ResponseEntity.ok(Map.of("valid", true));
  }

  private Long extractUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
      return java.util.Objects.requireNonNull(details.getUserId());
    }
    throw new UnauthorizedException("User not authenticated");
  }
}
