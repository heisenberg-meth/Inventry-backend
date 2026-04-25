package com.ims.platform.controller;

import com.ims.dto.CreatePlatformUserRequest;
import com.ims.model.User;
import com.ims.platform.service.PlatformUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Platform - Users", description = "Platform user management")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/platform/users")
@RequiredArgsConstructor
public class PlatformUserController {

  /** Minimum length enforced on a reset password submitted by an operator. */
  private static final int MIN_PASSWORD_LENGTH = 8;

  private final PlatformUserService platformUserService;

  @PostMapping
  @Operation(summary = "Create platform user (ROOT only)")
  @PreAuthorize("hasAuthority('ROLE_ROOT')")
  @ResponseStatus(HttpStatus.CREATED)
  public @NonNull User createPlatformUser(
      @Valid @NonNull @RequestBody CreatePlatformUserRequest request) {
    return Objects.requireNonNull(platformUserService.createPlatformUser(request));
  }

  @Operation(summary = "List platform users (ROOT, PLATFORM_ADMIN)")
  @GetMapping
  @PreAuthorize("hasAnyAuthority('ROLE_ROOT', 'ROLE_PLATFORM_ADMIN')")
  public @NonNull Page<User> getPlatformUsers(@NonNull Pageable pageable) {
    return Objects.requireNonNull(platformUserService.getPlatformUsers(pageable));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get platform user details")
  @PreAuthorize("hasAnyAuthority('ROLE_ROOT', 'ROLE_PLATFORM_ADMIN')")
  public Map<String, Object> getPlatformUser(@PathVariable long id) {
    return platformUserService.getPlatformUser(id);
  }

  @Operation(summary = "Update platform user profile (ROOT only)")
  @org.springframework.web.bind.annotation.PutMapping("/{id}")
  @PreAuthorize("hasAuthority('ROLE_ROOT')")
  public @NonNull User updatePlatformUser(
      @PathVariable long id,
      @Valid @NonNull @RequestBody CreatePlatformUserRequest request) {
    return Objects.requireNonNull(platformUserService.updatePlatformUser(id, request));
  }

  @Operation(summary = "Update platform user role (ROOT only)")
  @PatchMapping("/{id}/role")
  @PreAuthorize("hasAuthority('ROLE_ROOT')")
  public @NonNull User updateRole(
      @PathVariable long id, @NonNull @RequestBody Map<String, String> body) {
    String role = body.get("role");
    if (role == null) {
      throw new IllegalArgumentException("Role is required");
    }
    return Objects.requireNonNull(platformUserService.updatePlatformUserRole(id, role));
  }

  @PostMapping("/{id}/suspend")
  @Operation(summary = "Suspend platform user (ROOT only)")
  @PreAuthorize("hasAuthority('ROLE_ROOT')")
  public Map<String, String> suspendPlatformUser(@PathVariable long id) {
    platformUserService.suspendPlatformUser(id);
    return Map.of("message", "Platform user suspended successfully", "status", "SUSPENDED");
  }

  @PostMapping("/{id}/activate")
  @Operation(summary = "Activate platform user (ROOT only)")
  @PreAuthorize("hasAuthority('ROLE_ROOT')")
  public Map<String, String> activatePlatformUser(@PathVariable long id) {
    platformUserService.activatePlatformUser(id);
    return Map.of("message", "Platform user activated successfully", "status", "ACTIVE");
  }

  @PostMapping("/{id}/reset-password")
  @Operation(summary = "Reset platform user password (ROOT only)")
  @PreAuthorize("hasAuthority('ROLE_ROOT')")
  public Map<String, String> resetPassword(
      @PathVariable long id, @NonNull @RequestBody Map<String, String> body) {
    String newPassword = body.get("newPassword");
    if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException(
          "New password must be at least " + MIN_PASSWORD_LENGTH + " characters");
    }
    return platformUserService.resetPlatformUserPassword(id, newPassword);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Deactivate platform user (ROOT only)")
  @PreAuthorize("hasAuthority('ROLE_ROOT')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivatePlatformUser(@PathVariable long id) {
    platformUserService.deactivatePlatformUser(id);
  }
}
