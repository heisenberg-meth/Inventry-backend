package com.ims.tenant.controller;

import com.ims.dto.request.AssignPermissionsRequest;
import com.ims.dto.request.CreateRoleRequest;
import com.ims.model.Role;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/roles")
@RequiredArgsConstructor
@Tag(name = "Tenant - Roles", description = "Tenant-scoped role management")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

  private final RoleService roleService;

  @GetMapping
  @RequiresRole({"ADMIN"})
  @Operation(summary = "List tenant roles")
  public ResponseEntity<List<Role>> listRoles() {
    Long tenantId = getTenantId();
    return ResponseEntity.ok(roleService.findByTenant(tenantId));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Get role details with permissions")
  public ResponseEntity<Role> getRole(@NonNull @PathVariable Long id) {
    Long tenantId = getTenantId();
    return ResponseEntity.ok(roleService.findOne(tenantId, id));
  }

  @PostMapping
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Create a new role")
  public ResponseEntity<Role> createRole(@NonNull @Valid @RequestBody CreateRoleRequest request) {
    Long tenantId = getTenantId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(roleService.create(tenantId, request));
  }

  @PostMapping("/{id}/permissions")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Assign permissions to role")
  public ResponseEntity<Role> assignPermissions(
      @NonNull @PathVariable Long id,
      @NonNull @Valid @RequestBody AssignPermissionsRequest request) {
    Long tenantId = getTenantId();
    return ResponseEntity.ok(roleService.assignPermissions(tenantId, id, request));
  }

  private @NonNull Long getTenantId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
      return Objects.requireNonNull(details.getTenantId(), "Tenant ID must not be null");
    }
    throw new com.ims.shared.exception.UnauthorizedAccessException("User not authenticated");
  }
}
