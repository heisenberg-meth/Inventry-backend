package com.ims.tenant.controller;

import com.ims.dto.request.AssignPermissionsRequest;
import com.ims.dto.request.CreateRoleRequest;
import com.ims.model.Role;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/roles")
@RequiredArgsConstructor
@Tag(name = "Tenant - Roles", description = "Tenant-scoped role management")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

  private final RoleService roleService;

  @GetMapping
  @RequiresRole({ "TENANT_ADMIN" })
  @Operation(summary = "List tenant roles")
  public ResponseEntity<List<Role>> listRoles() {
    return ResponseEntity.ok(roleService.findAll());
  }

  @GetMapping("/{id}")
  @RequiresRole({ "TENANT_ADMIN" })
  @Operation(summary = "Get role details with permissions")
  public ResponseEntity<Role> getRole(@PathVariable long id) {
    return ResponseEntity.ok(roleService.findOne(id));
  }

  @PostMapping
  @RequiresRole({ "TENANT_ADMIN" })
  @Operation(summary = "Create a new role")
  public ResponseEntity<Role> createRole(@Valid @RequestBody CreateRoleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(roleService.create(request));
  }

  @PostMapping("/{id}/permissions")
  @RequiresRole({ "TENANT_ADMIN" })
  @Operation(summary = "Assign permissions to role")
  public ResponseEntity<Role> assignPermissions(
      @PathVariable long id, @Valid @RequestBody AssignPermissionsRequest request) {
    return ResponseEntity.ok(roleService.assignPermissions(id, request));
  }

}
