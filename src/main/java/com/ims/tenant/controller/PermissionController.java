package com.ims.tenant.controller;

import com.ims.model.Permission;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.repository.PermissionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/permissions")
@RequiredArgsConstructor
@Tag(name = "Tenant - Permissions", description = "View available permissions")
@SecurityRequirement(name = "bearerAuth")
public class PermissionController {

  private final PermissionRepository permissionRepository;

  @GetMapping
  @RequiresRole({ "ADMIN" })
  @Operation(summary = "List all available permissions")
  public ResponseEntity<List<Permission>> listPermissions() {
    return ResponseEntity.ok(permissionRepository.findAllByOrderByKeyAsc());
  }
}
