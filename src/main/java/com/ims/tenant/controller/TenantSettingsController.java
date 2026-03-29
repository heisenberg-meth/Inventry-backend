package com.ims.tenant.controller;

import com.ims.dto.request.UpdateTenantSettingsRequest;
import com.ims.dto.response.TenantResponse;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.TenantSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/settings")
@RequiredArgsConstructor
@Tag(name = "Tenant - Settings", description = "Tenant-level business configuration")
@SecurityRequirement(name = "bearerAuth")
public class TenantSettingsController {

  private final TenantSettingsService tenantSettingsService;

  @GetMapping
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Get tenant configurations (domain, sequence tracking)")
  public ResponseEntity<TenantResponse> getSettings() {
    Long tenantId = getTenantId();
    return ResponseEntity.ok(tenantSettingsService.getSettings(tenantId));
  }

  @PatchMapping
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Configure custom domains and business name")
  public ResponseEntity<TenantResponse> updateSettings(
      @Valid @RequestBody UpdateTenantSettingsRequest request) {
    Long tenantId = getTenantId();
    return ResponseEntity.ok(tenantSettingsService.updateSettings(tenantId, request));
  }

  private Long getTenantId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
      return details.getTenantId();
    }
    throw new SecurityException("Auth context missing for tenant settings");
  }
}
