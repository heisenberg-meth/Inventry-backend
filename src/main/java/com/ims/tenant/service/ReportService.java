package com.ims.tenant.service;

import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.product.ProductRepository;
import com.ims.shared.auth.SecurityContextAccessor;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.TenantContextException;
import com.ims.shared.notification.AlertRepository;
import com.ims.tenant.domain.pharmacy.PharmacyProductRepository;
import com.ims.tenant.repository.OrderRepository;

import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

  private final ProductRepository productRepository;
  private final OrderRepository orderRepository;
  private final PharmacyProductRepository pharmacyProductRepository;
  private final TenantRepository tenantRepository;
  private final AlertRepository alertRepository;
  private final JdbcTemplate jdbcTemplate;
  private final SecurityContextAccessor securityContextAccessor;

  private static final int DEFAULT_DAYS = 30;
  private static final int PERCENTAGE_BASE = 100;
  private static final int STATUS_PRIORITY_OK = 3;

  private int getExpiryThreshold() {
    Long tenantId = securityContextAccessor.requireTenantId();
    return tenantRepository
        .findById(tenantId)
        .map(Tenant::getExpiryThresholdDays)
        .orElse(DEFAULT_DAYS);
  }

  @Cacheable(value = "reports", key = "'purchases:' + #from + ':' + #to", cacheResolver = "tenantAwareCacheResolver")
  public Map<String, Object> getPurchasesReport(
      LocalDate from, LocalDate to) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new TenantContextException("Tenant not resolved from request");
    }

    LocalDateTime fromDt = Objects.requireNonNull(from, "from date required").atStartOfDay();
    LocalDateTime toDt = Objects.requireNonNull(to, "to date required").atTime(LocalTime.MAX);

    BigDecimal totalSpent = orderRepository.sumAmountByTypeAndDateRange(
        "PURCHASE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));

    long totalOrders = orderRepository.countByTypeAndDateRange(
        "PURCHASE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));
    BigDecimal avgOrderValue = totalOrders > 0
        ? totalSpent.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    Map<String, Object> analytics = new LinkedHashMap<>();
    analytics.put("period", Map.of("from", from, "to", to));
    analytics.put("total_spent", totalSpent);
    analytics.put("total_purchase_orders", totalOrders);
    analytics.put("average_purchase_value", avgOrderValue);
    analytics.put("cached_at", LocalDateTime.now().toString());

    return analytics;
  }

  @Cacheable(value = "dashboard", key = "'dashboard'", cacheResolver = "tenantAwareCacheResolver")
  public Map<String, Object> getDashboard() {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new TenantContextException("Tenant not resolved from request");
    }

    Map<String, Object> dashboard = new LinkedHashMap<>();

    try {
      Map<String, Object> stats = jdbcTemplate.queryForMap(
          "SELECT * FROM tenant_summary_stats WHERE tenant_id = ?", tenantId);

      dashboard.put("total_products", stats.get("total_products"));
      dashboard.put("low_stock_count", stats.get("low_stock_count"));
      dashboard.put("out_of_stock_count", stats.get("out_of_stock_count"));
      dashboard.put("today_sales_amount", stats.get("today_sales_amount"));
      dashboard.put("today_sales_count", stats.get("today_sales_count"));
      dashboard.put("today_purchases_amount", stats.get("today_purchases_amount"));
      dashboard.put("inventory_valuation", stats.get("inventory_valuation"));
      dashboard.put("last_refreshed_at", stats.get("last_refreshed_at"));
    } catch (Exception e) {
      log.warn("Materialized view fetch failed for tenant {}: {}. Falling back to real-time calculation.", tenantId,
          e.getMessage());

      LocalDateTime todayStart = LocalDate.now().atStartOfDay();

      // COMBINED OPTIMIZED QUERY: Fetch all stats in one trip
      String sql = """
              SELECT
                  (SELECT COUNT(*) FROM products WHERE tenant_id = ? AND is_active = true) as total_products,
                  (SELECT COUNT(*) FROM products WHERE tenant_id = ? AND is_active = true AND stock <= reorder_level) as low_stock_count,
                  (SELECT COUNT(*) FROM products WHERE tenant_id = ? AND is_active = true AND stock = 0) as out_of_stock_count,
                  (SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE tenant_id = ? AND type = 'SALE' AND created_at >= ?) as today_sales_amount,
                  (SELECT COUNT(*) FROM orders WHERE tenant_id = ? AND type = 'SALE' AND created_at >= ?) as today_sales_count,
                  (SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE tenant_id = ? AND type = 'PURCHASE' AND created_at >= ?) as today_purchases_amount,
                  (SELECT COALESCE(SUM(sale_price * stock), 0) FROM products WHERE tenant_id = ? AND is_active = true) as inventory_valuation
          """;

      Map<String, Object> fallbackStats = jdbcTemplate.queryForMap(sql,
          tenantId, tenantId, tenantId, tenantId, todayStart, tenantId, todayStart, tenantId, todayStart, tenantId);

      dashboard.putAll(fallbackStats);
    }

    // Expiring soon — only for PHARMACY tenants
    if ("PHARMACY".equals(securityContextAccessor.getBusinessType().orElse(null))) {
      LocalDate threshold = LocalDate.now().plusDays(getExpiryThreshold());
      long expiringSoon = pharmacyProductRepository.countExpiring(threshold);
      dashboard.put("expiring_soon_count", expiringSoon);
    }

    dashboard.put("category_distribution", getCategoryDistribution());
    dashboard.put("cached_at", Objects.requireNonNull(LocalDateTime.now()).toString());

    return dashboard;
  }

  public BigDecimal getInventoryValuation() {
    return productRepository.getTotalInventoryValue();
  }

  public List<Map<String, Object>> getCategoryDistribution() {
    return Objects.requireNonNull(
        productRepository.getCategoryDistribution().stream()
            .map(
                c -> {
                  Map<String, Object> item = new LinkedHashMap<>();
                  item.put("category_name", c.getCategoryName());
                  item.put("product_count", c.getProductCount());
                  return item;
                })
            .collect(Collectors.toList()));
  }

  @Cacheable(value = "reports", key = "'stock-report'", cacheResolver = "tenantAwareCacheResolver")
  public List<Map<String, Object>> getStockReport(String filter) {

    // Use optimized projection to avoid N+1 and memory bloat
    var products = productRepository.findStockReportView();
    List<Map<String, Object>> report = new ArrayList<>();
    int thresholdDays = getExpiryThreshold();

    for (var product : products) {
      String status;
      if (product.getStock() == 0) {
        status = "OUT_OF_STOCK";
      } else if (product.getStock() <= product.getReorderLevel()) {
        status = "LOW";
      } else {
        status = "OK";
      }

      // Check expiry from joined data
      LocalDate expiryDate = product.getExpiryDate();

      if (expiryDate != null && expiryDate.isBefore(LocalDate.now().plusDays(thresholdDays))) {
        status = "EXPIRING";
      }

      // Apply filter
      if ("low".equals(filter) && !"LOW".equals(status) && !"OUT_OF_STOCK".equals(status)) {
        continue;
      }
      if ("expiring".equals(filter) && !"EXPIRING".equals(status)) {
        continue;
      }

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("product_id", product.getId());
      item.put("product_name", product.getName());
      item.put("sku", product.getSku());
      item.put("current_stock", product.getStock());
      item.put("reorder_level", product.getReorderLevel());
      item.put("unit", product.getUnit());
      item.put("status", status);
      if (expiryDate != null) {
        item.put("expiry_date", expiryDate);
      }
      report.add(item);
    }

    // Sort by urgency: OUT_OF_STOCK > LOW > EXPIRING > OK
    report.sort(
        (a, b) -> {
          int p1 = statusPriority((String) a.get("status"));
          int p2 = statusPriority((String) b.get("status"));
          return Integer.compare(p1, p2);
        });

    return report;
  }

  @Cacheable(value = "reports", key = "'sales:' + #from + ':' + #to", cacheResolver = "tenantAwareCacheResolver")
  public Map<String, Object> getSalesAnalytics(
      LocalDate from, LocalDate to) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new TenantContextException("Tenant not resolved from request");
    }

    LocalDateTime fromDt = Objects.requireNonNull(from, "from date required").atStartOfDay();
    LocalDateTime toDt = Objects.requireNonNull(to, "to date required").atTime(LocalTime.MAX);

    BigDecimal totalRevenue = orderRepository.sumAmountByTypeAndDateRange(
        "SALE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));

    long totalOrders = orderRepository.countByTypeAndDateRange(
        "SALE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));
    BigDecimal avgOrderValue = totalOrders > 0
        ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    Map<String, Object> analytics = new LinkedHashMap<>();
    analytics.put("period", Map.of("from", from, "to", to));
    analytics.put("total_revenue", totalRevenue);
    analytics.put("total_orders", totalOrders);
    analytics.put("average_order_value", avgOrderValue);
    analytics.put("cached_at", Objects.requireNonNull(LocalDateTime.now()).toString());

    return analytics;
  }

  public Map<String, Object> getProfitLoss(
      LocalDate from, LocalDate to) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new TenantContextException("Tenant not resolved from request");
    }

    LocalDateTime fromDt = Objects.requireNonNull(from, "from date required").atStartOfDay();
    LocalDateTime toDt = Objects.requireNonNull(to, "to date required").atTime(LocalTime.MAX);

    BigDecimal salesRevenue = orderRepository.sumAmountByTypeAndDateRange(
        "SALE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));

    BigDecimal purchaseCost = orderRepository.sumAmountByTypeAndDateRange(
        "PURCHASE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));
    BigDecimal profit = salesRevenue.subtract(purchaseCost);

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("period", Map.of("from", from, "to", to));
    report.put("total_sales", salesRevenue);
    report.put("total_purchases", purchaseCost);
    report.put("gross_profit", profit);
    report.put(
        "margin_percentage",
        salesRevenue.compareTo(BigDecimal.ZERO) > 0
            ? profit
                .multiply(BigDecimal.valueOf(PERCENTAGE_BASE))
                .divide(salesRevenue, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO);

    return report;
  }

  public Map<String, Object> getGstReport(LocalDate from, LocalDate to) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new TenantContextException("Tenant not resolved from request");
    }

    LocalDateTime fromDt = Objects.requireNonNull(from, "from date required").atStartOfDay();
    LocalDateTime toDt = Objects.requireNonNull(to, "to date required").atTime(LocalTime.MAX);

    BigDecimal totalSalesTax = orderRepository.sumTaxAmountByTypeAndDateRange(
        "SALE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));

    BigDecimal totalPurchaseTax = orderRepository.sumTaxAmountByTypeAndDateRange(
        "PURCHASE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));

    Map<String, Object> gst = new LinkedHashMap<>();
    gst.put("period", Map.of("from", from, "to", to));
    gst.put("output_gst", totalSalesTax);
    gst.put("input_gst", totalPurchaseTax);
    gst.put("net_gst_payable", totalSalesTax.subtract(totalPurchaseTax));

    return gst;
  }

  public List<Map<String, Object>> getAlerts() {
    return Objects.requireNonNull(
        alertRepository
            .findAllByIsDismissedFalse()
            .stream()
            .map(
                a -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("id", a.getId());
                  map.put("type", a.getType());
                  map.put("severity", a.getSeverity());
                  map.put("message", a.getMessage());
                  map.put("resource_id", a.getResourceId());
                  map.put("created_at", a.getCreatedAt());
                  return map;
                })
            .collect(Collectors.toList()));
  }

  @Transactional
  public void dismissAlert(Long id) {
    alertRepository
        .findById(id)
        .ifPresent(
            a -> {
              a.setIsDismissed(true);
              a.setDismissedAt(Objects.requireNonNull(LocalDateTime.now()));
              alertRepository.save(a);
            });
  }

  private int statusPriority(String status) {
    if (status == null) {
      return STATUS_PRIORITY_OK;
    }
    return switch (status) {
      case "OUT_OF_STOCK" -> 0;
      case "LOW" -> 1;
      case "EXPIRING" -> 2;
      default -> STATUS_PRIORITY_OK;
    };
  }

}
