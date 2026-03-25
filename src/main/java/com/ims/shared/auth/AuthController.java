package com.ims.shared.auth;

import com.ims.dto.request.LoginRequest;
import com.ims.dto.response.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, Logout, and Token Refresh")
public class AuthController {

  private final AuthService authService;

  private static final int BEARER_PREFIX_LENGTH = 7;

  @PostMapping("/login")
  @Operation(
      summary = "Login",
      description = "Authenticate with email/password, returns JWT tokens")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/logout")
  @Operation(summary = "Logout", description = "Blacklists the JWT token in Redis")
  public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(BEARER_PREFIX_LENGTH);
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
    LoginResponse response = authService.refresh(refreshToken);
    return ResponseEntity.ok(response);
  }
}
