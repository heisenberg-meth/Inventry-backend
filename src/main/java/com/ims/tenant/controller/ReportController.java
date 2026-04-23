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
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ims.shared.utils.CsvExportService;
import com.ims.tenant.repository.OrderRepository;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant - Reports", description = "Reports and analytics")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

  private final ReportService reportService;
  private final StockMovementRepository stockMovementRepository;
  private final OrderRepository orderRepository;
  private final CsvExportService csvExportService;

  @GetMapping("/reports/orders/export")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Export orders as CSV")
  public ResponseEntity<String> exportOrders(
      @RequestParam(required = false) String type,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    
    var orders = type != null 
        ? orderRepository.findByType(type, Pageable.unpaged()).getContent()
        : orderRepository.findAll(Pageable.unpaged()).getContent();
        
    var filtered = orders.stream()
        .filter(o -> !o.getCreatedAt().toLocalDate().isBefore(from) && !o.getCreatedAt().toLocalDate().isAfter(to))
        .map(o -> {
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("ID", o.getId());
          map.put("Type", o.getType());
          map.put("Status", o.getStatus());
          map.put("Total", o.getTotalAmount());
          map.put("Date", o.getCreatedAt());
          return map;
        })
        .collect(Collectors.toList());

    String csv = csvExportService.exportToCsv(List.of("ID", "Type", "Status", "Total", "Date"), filtered);
    
    return ResponseEntity.ok()
        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=orders.csv")
        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv")
        .body(csv);
  }

  @GetMapping("/reports/sales/export")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Export sales as CSV")
  public ResponseEntity<String> exportSales(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    
    var orders = orderRepository.findByType("SALE", Pageable.unpaged()).getContent();
    var filtered = orders.stream()
        .filter(o -> !o.getCreatedAt().toLocalDate().isBefore(from) && !o.getCreatedAt().toLocalDate().isAfter(to))
        .map(o -> {
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("Order ID", o.getId());
          map.put("Customer ID", o.getCustomerId());
          map.put("Total Amount", o.getTotalAmount());
          map.put("Tax", o.getTaxAmount());
          map.put("Discount", o.getDiscount());
          map.put("Date", o.getCreatedAt());
          return map;
        })
        .collect(Collectors.toList());

    String csv = csvExportService.exportToCsv(List.of("Order ID", "Customer ID", "Total Amount", "Tax", "Discount", "Date"), filtered);
    
    return ResponseEntity.ok()
        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sales.csv")
        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv")
        .body(csv);
  }

  @GetMapping("/reports/purchases/export")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Export purchases as CSV")
  public ResponseEntity<String> exportPurchases(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    
    var orders = orderRepository.findByType("PURCHASE", Pageable.unpaged()).getContent();
    var filtered = orders.stream()
        .filter(o -> !o.getCreatedAt().toLocalDate().isBefore(from) && !o.getCreatedAt().toLocalDate().isAfter(to))
        .map(o -> {
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("Order ID", o.getId());
          map.put("Supplier ID", o.getSupplierId());
          map.put("Total Amount", o.getTotalAmount());
          map.put("Tax", o.getTaxAmount());
          map.put("Date", o.getCreatedAt());
          return map;
        })
        .collect(Collectors.toList());

    String csv = csvExportService.exportToCsv(List.of("Order ID", "Supplier ID", "Total Amount", "Tax", "Date"), filtered);
    
    return ResponseEntity.ok()
        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=purchases.csv")
        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv")
        .body(csv);
  }

  @GetMapping("/reports/purchases")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Purchases analytics with date range")
  public ResponseEntity<Map<String, Object>> getPurchasesReport(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NonNull LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NonNull LocalDate to) {
    return ResponseEntity.ok(reportService.getPurchasesReport(Objects.requireNonNull(from), Objects.requireNonNull(to)));
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
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NonNull LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NonNull LocalDate to) {
    return ResponseEntity.ok(reportService.getSalesAnalytics(Objects.requireNonNull(from), Objects.requireNonNull(to)));
  }

  @GetMapping("/reports/profit-loss")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Profit & Loss report")
  public ResponseEntity<Map<String, Object>> getProfitLoss(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NonNull LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NonNull LocalDate to) {
    return ResponseEntity.ok(reportService.getProfitLoss(Objects.requireNonNull(from), Objects.requireNonNull(to)));
  }

  @GetMapping("/reports/gst")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "GST summary report")
  public ResponseEntity<Map<String, Object>> getGstReport(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NonNull LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NonNull LocalDate to) {
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
    reportService.dismissAlert(id);
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
    return ResponseEntity.ok(
        stockMovementRepository.findByFilters(productId, from, to, pageable));
  }
}
