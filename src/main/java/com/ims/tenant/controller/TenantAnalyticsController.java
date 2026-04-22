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
@RequestMapping("/tenant/analytics")
@RequiredArgsConstructor
@Tag(name = "Tenant - Analytics", description = "Tenant-level data insights")
@SecurityRequirement(name = "bearerAuth")
public class TenantAnalyticsController {

    private final TenantAnalyticsService analyticsService;

    @GetMapping("/revenue-trend")
    @Operation(summary = "Get revenue trend by month")
    public ResponseEntity<List<Map<String, Object>>> getRevenueTrend() {
        return ResponseEntity.ok(analyticsService.getRevenueTrend());
    }

    @GetMapping("/top-products")
    @Operation(summary = "Get top performing products")
    public ResponseEntity<List<Map<String, Object>>> getTopProducts() {
        return ResponseEntity.ok(analyticsService.getTopProducts());
    }

    @GetMapping("/categories")
    @Operation(summary = "Get category distribution stats")
    public ResponseEntity<List<Map<String, Object>>> getCategoryStats() {
        // Reuse logic from ReportController via AnalyticsService
        return ResponseEntity.ok(analyticsService.getQuickStats()); // Placeholder or dedicated logic
    }

    @GetMapping("/order-statuses")
    @Operation(summary = "Get distribution of order statuses")
    public ResponseEntity<List<Map<String, Object>>> getOrderStatusStats() {
        return ResponseEntity.ok(analyticsService.getOrderStatusStats());
    }

    @GetMapping("/quick-stats")
    @Operation(summary = "Get quick overview statistics")
    public ResponseEntity<List<Map<String, Object>>> getQuickStats() {
        return ResponseEntity.ok(analyticsService.getQuickStats());
    }
}
