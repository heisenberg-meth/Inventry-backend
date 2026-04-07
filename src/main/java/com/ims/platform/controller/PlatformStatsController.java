package com.ims.platform.controller;

import com.ims.platform.repository.TenantRepository;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@Tag(name = "Platform - Stats", description = "Platform-wide metrics")
@SecurityRequirement(name = "bearerAuth")
public class PlatformStatsController {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;

  @GetMapping("/stats")
  @RequiresRole({"ROOT"})
  @Operation(summary = "Platform-wide metrics")
  public ResponseEntity<Map<String, Object>> getStats() {
    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("total_tenants", tenantRepository.count());
    stats.put("total_users", userRepository.count());
    return ResponseEntity.ok(stats);
  }
}
