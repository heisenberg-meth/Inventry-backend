package com.ims.platform.controller;

import com.ims.platform.service.PlatformAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/analytics")
@RequiredArgsConstructor
@Tag(name = "Platform - Analytics", description = "System-wide analytics for ROOT users")
@SecurityRequirement(name = "bearerAuth")
public class PlatformAnalyticsController {

  private final PlatformAnalyticsService analyticsService;

  @GetMapping("/revenue")
  @PreAuthorize("hasRole('ROOT')")
  @Operation(summary = "Get revenue analytics (MRR/ARR)")
  public ResponseEntity<Map<String, Object>> getRevenue() {
    return ResponseEntity.ok(analyticsService.getRevenueAnalytics());
  }

  @GetMapping("/tenants")
  @PreAuthorize("hasRole('ROOT')")
  @Operation(summary = "Get tenant growth analytics")
  public ResponseEntity<Map<String, Object>> getTenants() {
    return ResponseEntity.ok(analyticsService.getTenantAnalytics());
  }

  @GetMapping("/usage")
  @PreAuthorize("hasRole('ROOT')")
  @Operation(summary = "Get system usage analytics")
  public ResponseEntity<Map<String, Object>> getUsage() {
    return ResponseEntity.ok(analyticsService.getUsageAnalytics());
  }
}
