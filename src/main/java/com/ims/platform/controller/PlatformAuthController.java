package com.ims.platform.controller;

import com.ims.dto.request.LoginRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.shared.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Platform - Authentication", description = "Login for platform administrators")
public class PlatformAuthController {

  private final AuthService authService;

  @PostMapping("/login")
  @Operation(summary = "Platform Admin Login", description = "Authenticate with email/password, NO companyCode required")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    log.info("Platform login attempt: email={}", request.getEmail());
    LoginResponse response = authService.platformLogin(request);
    return ResponseEntity.ok(response);
  }
}
