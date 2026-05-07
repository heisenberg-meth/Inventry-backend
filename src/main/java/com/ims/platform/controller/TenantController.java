package com.ims.platform.controller;

import com.ims.dto.request.AssignPlanRequest;
import com.ims.dto.request.CreateTenantRequest;
import com.ims.dto.request.CreateTenantUserRequest;
import com.ims.dto.response.TenantResponse;
import com.ims.dto.response.UserResponse;
import com.ims.platform.service.TenantService;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
@Tag(name = "Platform - Tenants", description = "Platform-level tenant management")
@SecurityRequirement(name = "bearerAuth")
public class TenantController {

  private final TenantService tenantService;
  private final com.ims.shared.auth.AuthService authService;
  private final com.ims.shared.audit.AuditLogService auditLogService;

  @GetMapping
  @PreAuthorize("hasRole('ROOT')")
  @Operation(summary = "List all tenants", description = "Paginated list of all tenants")
  public ResponseEntity<Page<TenantResponse>> getAllTenants(Pageable pageable) {
    return ResponseEntity.ok(tenantService.getAllTenants(pageable));
  }

  @PostMapping
  @PreAuthorize("hasRole('ROOT')")
  @Operation(summary = "Create new tenant")
  public ResponseEntity<TenantResponse> createTenant(
      @Valid @RequestBody CreateTenantRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.createTenant(request));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ROOT')")
  @Operation(summary = "Get tenant details")
  public ResponseEntity<TenantResponse> getTenant(@PathVariable Long id) {
    return ResponseEntity.ok(tenantService.getTenantById(id));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('ROOT')")
  @Operation(summary = "Update tenant plan/status")
  public ResponseEntity<TenantResponse> updateTenant(
      @PathVariable Long id, @Valid @RequestBody CreateTenantRequest request) {
    return ResponseEntity.ok(tenantService.updateTenant(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ROOT')")
  @Operation(summary = "Deactivate tenant (soft delete)")
  public ResponseEntity<Void> deactivateTenant(@PathVariable Long id) {
    tenantService.deactivateTenant(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/suspend")
  @PreAuthorize("hasRole('ROOT')")
  @Operation(summary = "Suspend a tenant")
  public ResponseEntity<Map<String, String>> suspendTenant(@PathVariable Long id) {
    return ResponseEntity.ok(tenantService.suspendTenant(id));
  }

  @PostMapping("/{id}/activate")
  @PreAuthorize("hasRole('ROOT')")
  @Operation(summary = "Activate tenant")
  public ResponseEntity<Map<String, String>> activate(@PathVariable Long id) {
    return ResponseEntity.ok(tenantService.activateTenant(id));
  }

  @PostMapping("/{id}/impersonate")
  @PreAuthorize("hasRole('ROOT')")
  @Operation(summary = "Impersonate tenant admin")
  public ResponseEntity<com.ims.dto.response.LoginResponse> impersonate(@PathVariable Long id) {
    return ResponseEntity.ok(authService.impersonateTenant(id));
  }

  @GetMapping("/{id}/audit")
  @PreAuthorize("hasAnyRole('ROOT', 'PLATFORM_ADMIN')")
  @Operation(summary = "Get audit logs for a specific tenant")
  public ResponseEntity<Page<com.ims.model.AuditLog>> getTenantAuditLogs(@PathVariable Long id, Pageable pageable) {
    try {
      com.ims.shared.auth.TenantContext.setTenantId(id);
      return ResponseEntity.ok(auditLogService.getTenantLogs(pageable));
    } finally {
      com.ims.shared.auth.TenantContext.clear();
    }
  }

  @GetMapping("/{id}/users")
  @PreAuthorize("hasAnyRole('ROOT', 'PLATFORM_ADMIN')")
  @Operation(summary = "List tenant users", description = "List users of a specific tenant with optional search")
  public ResponseEntity<Page<UserResponse>> getTenantUsers(
      @PathVariable Long id,
      @RequestParam(required = false) String q,
      Pageable pageable) {
    return ResponseEntity.ok(tenantService.getTenantUsers(id, q, pageable));
  }

  @DeleteMapping("/{id}/users/{userId}")
  @PreAuthorize("hasRole('ROOT')")
  @Operation(summary = "Platform hard-delete a tenant user")
  public ResponseEntity<Void> hardDeleteTenantUser(@PathVariable Long id, @PathVariable Long userId) {
    tenantService.hardDeleteTenantUser(id, userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/users/{userId}/reset-password")
  @PreAuthorize("hasAnyRole('ROOT', 'PLATFORM_ADMIN')")
  @Operation(summary = "Reset a tenant user's password")
  public ResponseEntity<Map<String, String>> resetTenantUserPassword(
      @PathVariable Long userId,
      @RequestBody(required = false) Map<String, String> body) {
    String newPassword = (body != null) ? body.get("newPassword") : null;
    return ResponseEntity.ok(tenantService.resetTenantUserPassword(userId, newPassword));
  }

  @PostMapping("/{id}/assign-plan")
  @PreAuthorize("hasRole('ROOT')")
  @Operation(summary = "Assign a subscription plan to a tenant")
  public ResponseEntity<Map<String, Object>> assignPlan(
      @PathVariable Long id, @Valid @RequestBody AssignPlanRequest request) {
    return ResponseEntity.ok(tenantService.assignPlan(id, request));
  }

  @GetMapping("/{id}/subscription")
  @PreAuthorize("hasAnyRole('ROOT', 'PLATFORM_ADMIN')")
  @Operation(summary = "Get tenant subscription info")
  public ResponseEntity<Map<String, Object>> getSubscription(@PathVariable Long id) {
    return ResponseEntity.ok(tenantService.getSubscription(id));
  }

  @PostMapping("/{tenantId}/users")
  @PreAuthorize("hasAnyRole('ROOT', 'PLATFORM_ADMIN')")
  @Operation(summary = "Create tenant admin user")
  public ResponseEntity<UserResponse> createTenantAdmin(
      @PathVariable Long tenantId,
      @Valid @RequestBody CreateTenantUserRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(tenantService.createTenantUser(tenantId, request));
  }
}
