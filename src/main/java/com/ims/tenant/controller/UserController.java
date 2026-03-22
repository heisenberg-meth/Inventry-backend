package com.ims.tenant.controller;

import com.ims.dto.request.CreateUserRequest;
import com.ims.dto.response.UserResponse;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @GetMapping
    @RequiresRole({"ADMIN", "MANAGER"})
    @Operation(summary = "List tenant users")
    public ResponseEntity<Page<UserResponse>> getUsers(Pageable pageable) {
        return ResponseEntity.ok(userService.getUsers(pageable));
    }

    @GetMapping("/{id}")
    @RequiresRole({"ADMIN", "MANAGER"})
    @Operation(summary = "Get user details")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PatchMapping("/{id}/role")
    @RequiresRole({"ADMIN"})
    @Operation(summary = "Update user role")
    public ResponseEntity<UserResponse> updateRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }
        return ResponseEntity.ok(userService.updateRole(id, role));
    }

    @DeleteMapping("/{id}")
    @RequiresRole({"ADMIN"})
    @Operation(summary = "Deactivate user (soft delete)")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }
}
