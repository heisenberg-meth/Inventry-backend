package com.ims.tenant.controller;

import com.ims.tenant.service.TenantAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/ai")
@RequiredArgsConstructor
@Tag(name = "Tenant - AI", description = "AI-powered insights and forecasting")
@SecurityRequirement(name = "bearerAuth")
public class TenantAiController {

  private final TenantAnalyticsService analyticsService;

  @GetMapping("/health")
  @Operation(summary = "Get system health score")
  public ResponseEntity<Map<String, Object>> getHealth() {
    return ResponseEntity.ok(analyticsService.getAiHealth());
  }

  @GetMapping("/recommendations")
  @Operation(summary = "Get AI-generated recommendations")
  public ResponseEntity<List<Map<String, Object>>> getRecommendations() {
    return ResponseEntity.ok(analyticsService.getAiRecommendations());
  }

  @GetMapping("/demand-forecast")
  @Operation(summary = "Get AI demand forecasts")
  public ResponseEntity<List<Map<String, Object>>> getDemandForecast() {
    return ResponseEntity.ok(analyticsService.getAiDemandForecast());
  }

  @GetMapping("/anomalies")
  @Operation(summary = "Get detected system anomalies")
  public ResponseEntity<List<Map<String, Object>>> getAnomalies() {
    return ResponseEntity.ok(analyticsService.getAiAnomalies());
  }
}
