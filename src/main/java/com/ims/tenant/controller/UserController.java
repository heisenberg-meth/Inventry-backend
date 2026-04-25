package com.ims.tenant.controller;

import com.ims.dto.request.AssignPermissionsRequest;
import com.ims.dto.request.CreateUserRequest;
import com.ims.dto.response.UserResponse;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.UserService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/users")
@RequiredArgsConstructor
@Tag(name = "Tenant - Users", description = "Tenant-scoped user management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

  private final UserService userService;

  @PostMapping
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Create tenant user")
  public ResponseEntity<UserResponse> createUser(
      @Valid @RequestBody @NonNull CreateUserRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(userService.createUser(Objects.requireNonNull(request)));
  }

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "List tenant users")
  public ResponseEntity<Page<UserResponse>> getUsers(@NonNull Pageable pageable) {
    return ResponseEntity.ok(userService.getUsers(Objects.requireNonNull(pageable)));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Get user details")
  public ResponseEntity<UserResponse> getUser(@PathVariable long id) {
    return ResponseEntity.ok(userService.getUserById(id));
  }

  @PatchMapping("/{id}/role")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Update user role")
  public ResponseEntity<UserResponse> updateRole(
      @PathVariable long id, @RequestBody Map<String, String> body) {
    String role = body.get("role");
    if (role == null || role.isBlank()) {
      throw new IllegalArgumentException("Role is required");
    }
    return ResponseEntity.ok(
        userService.updateRole(id, Objects.requireNonNull(role)));
  }

  @PostMapping("/{id}/permissions")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Assign custom permissions to user")
  public ResponseEntity<UserResponse> assignPermissions(
      @PathVariable long id,
      @Valid @RequestBody @NonNull AssignPermissionsRequest request) {
    return ResponseEntity.ok(
        userService.assignPermissions(id, Objects.requireNonNull(request)));
  }

  @DeleteMapping("/{id}")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Deactivate user (soft delete)")
  public ResponseEntity<Void> deactivateUser(@PathVariable long id) {
    userService.deactivateUser(id);
    return ResponseEntity.noContent().build();
  }
}
