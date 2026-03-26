package com.ims.platform.controller;

import com.ims.dto.request.CreateTenantRequest;
import com.ims.dto.response.TenantResponse;
import com.ims.platform.service.TenantService;
import com.ims.shared.rbac.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
@Tag(name = "Platform - Tenants", description = "Platform-level tenant management")
@SecurityRequirement(name = "bearerAuth")
public class TenantController {

  private final TenantService tenantService;

  @GetMapping
  @RequiresRole({"ROOT"})
  @Operation(summary = "List all tenants", description = "Paginated list of all tenants")
  public ResponseEntity<Page<TenantResponse>> getAllTenants(Pageable pageable) {
    return ResponseEntity.ok(tenantService.getAllTenants(pageable));
  }

  @PostMapping
  @RequiresRole({"ROOT"})
  @Operation(summary = "Create new tenant")
  public ResponseEntity<TenantResponse> createTenant(
      @Valid @RequestBody CreateTenantRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.createTenant(request));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ROOT"})
  @Operation(summary = "Get tenant details")
  public ResponseEntity<TenantResponse> getTenant(@PathVariable Long id) {
    return ResponseEntity.ok(tenantService.getTenantById(id));
  }

  @PatchMapping("/{id}")
  @RequiresRole({"ROOT"})
  @Operation(summary = "Update tenant plan/status")
  public ResponseEntity<TenantResponse> updateTenant(
      @PathVariable Long id, @RequestBody CreateTenantRequest request) {
    return ResponseEntity.ok(tenantService.updateTenant(id, request));
  }

  @DeleteMapping("/{id}")
  @RequiresRole({"ROOT"})
  @Operation(summary = "Deactivate tenant (soft delete)")
  public ResponseEntity<Void> deactivateTenant(@PathVariable Long id) {
    tenantService.deactivateTenant(id);
    return ResponseEntity.noContent().build();
  }
}
