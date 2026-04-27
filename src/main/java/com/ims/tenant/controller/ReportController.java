package com.ims.tenant.controller;

import com.ims.model.StockMovement;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant - Reports", description = "Reports and analytics")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

  private final ReportService reportService;
  private final StockMovementRepository stockMovementRepository;
  private final com.ims.tenant.service.ExportService exportService;

  @GetMapping("/reports/orders/export")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Export orders as CSV (Streaming)")
  public void exportOrders(
      @RequestParam(required = false) String type,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
    
    exportService.exportOrdersCsv(response, type, from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX));
  }

  @GetMapping("/reports/sales/export")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Export sales as CSV (Streaming)")
  public void exportSales(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
    
    exportService.exportOrdersCsv(response, "SALE", from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX));
  }

  @GetMapping("/reports/purchases/export")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Export purchases as CSV (Streaming)")
  public void exportPurchases(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
    
    exportService.exportOrdersCsv(response, "PURCHASE", from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX));
  }

  @GetMapping("/reports/products/export")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Export entire product catalog as CSV (Streaming)")
  public void exportProducts(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
    exportService.exportProductsCsv(response);
  }

  @GetMapping("/reports/purchases")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Purchases analytics with date range")
  public ResponseEntity<Map<String, Object>> getPurchasesReport(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(
        reportService.getPurchasesReport(Objects.requireNonNull(from), Objects.requireNonNull(to)));
  }

  @GetMapping("/reports/dashboard")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Dashboard KPIs")
  public ResponseEntity<Map<String, Object>> getDashboard() {
    return ResponseEntity.ok(reportService.getDashboard());
  }

  @GetMapping("/reports/stock")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Stock status report")
  public ResponseEntity<List<Map<String, Object>>> getStockReport(
      @RequestParam(defaultValue = "all") String filter) {
    return ResponseEntity.ok(reportService.getStockReport(filter));
  }

  @GetMapping("/reports/sales")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Sales analytics with date range")
  public ResponseEntity<Map<String, Object>> getSalesReport(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(
        reportService.getSalesAnalytics(Objects.requireNonNull(from), Objects.requireNonNull(to)));
  }

  @GetMapping("/reports/profit-loss")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Profit & Loss report")
  public ResponseEntity<Map<String, Object>> getProfitLoss(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(
        reportService.getProfitLoss(Objects.requireNonNull(from), Objects.requireNonNull(to)));
  }

  @GetMapping("/reports/gst")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "GST summary report")
  public ResponseEntity<Map<String, Object>> getGstReport(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(reportService.getGstReport(from, to));
  }

  @GetMapping("/reports/alerts")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Get critical system alerts")
  public ResponseEntity<List<Map<String, Object>>> getAlerts() {
    return ResponseEntity.ok(reportService.getAlerts());
  }

  @org.springframework.web.bind.annotation.PatchMapping("/reports/alerts/{id}/dismiss")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Dismiss an alert")
  public ResponseEntity<Void> dismissAlert(@PathVariable Long id) {
    reportService.dismissAlert(Objects.requireNonNull(id));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/audit")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Full audit log")
  public ResponseEntity<Page<StockMovement>> getAuditLog(
      @RequestParam(required = false) Long productId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to,
      Pageable pageable) {
    return ResponseEntity.ok(stockMovementRepository.findByFilters(productId, from, to, pageable));
  }
}
