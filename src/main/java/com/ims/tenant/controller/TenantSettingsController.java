package com.ims.tenant.controller;

import com.ims.dto.request.UpdateTenantSettingsRequest;
import com.ims.dto.response.TenantResponse;
import com.ims.tenant.service.TenantSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get tenant configurations (domain, sequence tracking)")
  public ResponseEntity<TenantResponse> getSettings() {
    return ResponseEntity.ok(tenantSettingsService.getSettings());
  }

  @PatchMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Configure custom workspace slugs and business name")
  public ResponseEntity<TenantResponse> updateSettings(
      @Valid @RequestBody UpdateTenantSettingsRequest request) {
    return ResponseEntity.ok(tenantSettingsService.updateSettings(Objects.requireNonNull(request)));
  }
}
