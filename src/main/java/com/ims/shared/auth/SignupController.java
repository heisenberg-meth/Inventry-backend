package com.ims.shared.auth;

import com.ims.dto.request.SignupRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/signup")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class SignupController {

  private final SignupService signupService;

  @PostMapping
  @Operation(
      summary = "Registration for new business",
      description = "Creates a new tenant and its first admin user.")
  public ResponseEntity<Map<String, String>> signup(@Valid @RequestBody SignupRequest request) {
    signupService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("message", "Business registered successfully. You can now login."));
  }
}
