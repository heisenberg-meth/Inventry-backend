package com.ims.tenant.controller;

import com.ims.model.StockMovement;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant - Reports", description = "Reports and analytics")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;
    private final StockMovementRepository stockMovementRepository;

    @GetMapping("/reports/dashboard")
    @RequiresRole({"ADMIN", "MANAGER"})
    @Operation(summary = "Dashboard KPIs")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Long tenantId = TenantContext.get();
        return ResponseEntity.ok(reportService.getDashboard(tenantId));
    }

    @GetMapping("/reports/stock")
    @RequiresRole({"ADMIN", "MANAGER"})
    @Operation(summary = "Stock status report")
    public ResponseEntity<List<Map<String, Object>>> getStockReport(
            @RequestParam(defaultValue = "all") String filter) {
        Long tenantId = TenantContext.get();
        return ResponseEntity.ok(reportService.getStockReport(tenantId, filter));
    }

    @GetMapping("/reports/sales")
    @RequiresRole({"ADMIN", "MANAGER"})
    @Operation(summary = "Sales analytics with date range")
    public ResponseEntity<Map<String, Object>> getSalesReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long tenantId = TenantContext.get();
        return ResponseEntity.ok(reportService.getSalesAnalytics(tenantId, from, to));
    }

    @GetMapping("/reports/profit-loss")
    @RequiresRole({"ADMIN"})
    @Operation(summary = "Profit & Loss report")
    public ResponseEntity<Map<String, Object>> getProfitLoss(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long tenantId = TenantContext.get();
        return ResponseEntity.ok(reportService.getProfitLoss(tenantId, from, to));
    }

    @GetMapping("/audit")
    @RequiresRole({"ADMIN"})
    @Operation(summary = "Full audit log")
    public ResponseEntity<Page<StockMovement>> getAuditLog(
            @RequestParam(required = false) Long product_id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {
        Long tenantId = TenantContext.get();
        return ResponseEntity.ok(stockMovementRepository.findByFilters(tenantId, product_id, from, to, pageable));
    }
}
