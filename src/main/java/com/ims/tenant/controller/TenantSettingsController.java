package com.ims.tenant.controller;

import com.ims.dto.request.UpdateTenantSettingsRequest;
import com.ims.dto.response.TenantResponse;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.TenantSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/settings")
@RequiredArgsConstructor
@Tag(name = "Tenant - Settings", description = "Tenant-level business configuration")
@SecurityRequirement(name = "bearerAuth")
public class TenantSettingsController {

  private final TenantSettingsService tenantSettingsService;
  private final com.ims.shared.auth.SecurityContextAccessor securityContext;

  @GetMapping
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Get tenant configurations (domain, sequence tracking)")
  public ResponseEntity<TenantResponse> getSettings() {
    Long tenantId = securityContext.requireTenantId();
    return ResponseEntity.ok(tenantSettingsService.getSettings(tenantId));
  }

  @PatchMapping
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Configure custom workspace slugs and business name")
  public ResponseEntity<TenantResponse> updateSettings(
      @Valid @RequestBody UpdateTenantSettingsRequest request) {
    Long tenantId = securityContext.requireTenantId();
    return ResponseEntity.ok(
        tenantSettingsService.updateSettings(tenantId, Objects.requireNonNull(request)));
  }

}
