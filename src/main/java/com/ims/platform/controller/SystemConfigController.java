package com.ims.platform.controller;

import com.ims.model.SystemConfig;
import com.ims.platform.service.SystemConfigService;
import com.ims.shared.rbac.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/config")
@RequiredArgsConstructor
@Tag(name = "Platform - Config", description = "Global system configuration and feature flags")
@SecurityRequirement(name = "bearerAuth")
public class SystemConfigController {

  private final SystemConfigService systemConfigService;

  @GetMapping
  @RequiresRole({"ROOT"})
  @Operation(summary = "List all global configs")
  public ResponseEntity<List<SystemConfig>> list() {
    return ResponseEntity.ok(systemConfigService.getAllConfigs());
  }

  @PatchMapping("/{key}")
  @RequiresRole({"ROOT"})
  @Operation(summary = "Update global feature flag/config")
  public ResponseEntity<SystemConfig> update(
      @PathVariable @NonNull String key, @RequestBody Map<String, String> body) {
    String value = body.get("value");
    if (value == null) {
      throw new IllegalArgumentException("Value is required");
    }
    return ResponseEntity.ok(
        systemConfigService.updateConfig(
            Objects.requireNonNull(key), Objects.requireNonNull(value)));
  }
}
