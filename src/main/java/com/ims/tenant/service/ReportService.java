package com.ims.tenant.service;

import com.ims.category.CategoryRepository;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.product.ProductRepository;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.domain.pharmacy.PharmacyProductRepository;
import com.ims.tenant.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

  private final ProductRepository productRepository;
  private final OrderRepository orderRepository;
  private final PharmacyProductRepository pharmacyProductRepository;
  private final TenantRepository tenantRepository;
  private final CategoryRepository categoryRepository;
  private final com.ims.shared.notification.AlertRepository alertRepository;

  private static final int DEFAULT_DAYS = 30;
  private static final int PERCENTAGE_BASE = 100;
  private static final int STATUS_PRIORITY_OK = 3;

  private int getExpiryThreshold() {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant not resolved from request");
    }
    return tenantRepository
        .findById(tenantId)
        .map(Tenant::getExpiryThresholdDays)
        .orElse(DEFAULT_DAYS);
  }

  @Cacheable(
      value = "reports",
      key = "'purchases:' + #from + ':' + #to",
      cacheResolver = "tenantAwareCacheResolver")
  public @NonNull Map<String, Object> getPurchasesReport(
      @NonNull LocalDate from, @NonNull LocalDate to) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant not resolved from request");
    }

    LocalDateTime fromDt = Objects.requireNonNull(from, "from date required").atStartOfDay();
    LocalDateTime toDt = Objects.requireNonNull(to, "to date required").atTime(LocalTime.MAX);

    BigDecimal totalSpent =
        orderRepository.sumAmountByTypeAndDateRange(
            "PURCHASE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));

    long totalOrders =
        orderRepository.countByTypeAndDateRange(
            "PURCHASE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));
    BigDecimal avgOrderValue =
        totalOrders > 0
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
  public @NonNull Map<String, Object> getDashboard() {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant not resolved from request");
    }

    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

    long totalProducts = productRepository.countActive();
    long lowStockCount = productRepository.countLowStock();
    long outOfStockCount = productRepository.countOutOfStock();

    BigDecimal todaySalesAmount =
        orderRepository.sumAmountByTypeAndDateRange(
            "SALE", Objects.requireNonNull(todayStart), Objects.requireNonNull(todayEnd));

    long todaySalesCount =
        orderRepository.countByTypeAndDateRange(
            "SALE", Objects.requireNonNull(todayStart), Objects.requireNonNull(todayEnd));

    BigDecimal todayPurchasesAmount =
        orderRepository.sumAmountByTypeAndDateRange(
            "PURCHASE", Objects.requireNonNull(todayStart), Objects.requireNonNull(todayEnd));

    Map<String, Object> dashboard = new LinkedHashMap<>();
    dashboard.put("total_products", totalProducts);
    dashboard.put("low_stock_count", lowStockCount);
    dashboard.put("out_of_stock_count", outOfStockCount);
    dashboard.put("today_sales_amount", todaySalesAmount);
    dashboard.put("today_sales_count", todaySalesCount);
    dashboard.put("today_purchases_amount", todayPurchasesAmount);

    // Expiring soon — only for PHARMACY tenants
    String businessType = getBusinessType();
    if ("PHARMACY".equals(businessType)) {
      LocalDate threshold = LocalDate.now().plusDays(getExpiryThreshold());
      long expiringSoon = pharmacyProductRepository.countExpiring(threshold);
      dashboard.put("expiring_soon_count", expiringSoon);
    }

    dashboard.put("inventory_valuation", getInventoryValuation(tenantId));
    dashboard.put("category_distribution", getCategoryDistribution(tenantId));

    dashboard.put("cached_at", LocalDateTime.now().toString());
    return dashboard;
  }

  public BigDecimal getInventoryValuation(Long tenantId) {
    return productRepository.getTotalInventoryValue(tenantId);
  }

  public List<Map<String, Object>> getCategoryDistribution(Long tenantId) {
    return productRepository.getCategoryDistribution(tenantId).stream()
        .map(
            c -> {
              Map<String, Object> item = new LinkedHashMap<>();
              item.put("category_name", c.getCategoryName());
              item.put("product_count", c.getProductCount());
              return item;
            })
        .collect(Collectors.toList());
  }

  @Cacheable(value = "reports", key = "'stock-report'", cacheResolver = "tenantAwareCacheResolver")
  public @NonNull List<Map<String, Object>> getStockReport(@Nullable String filter) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant not resolved");
    }

    // Use findAllWithDetails to avoid N+1 query for PharmacyProduct details
    var products = productRepository.findAllWithDetails(tenantId, Pageable.unpaged()).getContent();
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

  @Cacheable(
      value = "reports",
      key = "'sales:' + #from + ':' + #to",
      cacheResolver = "tenantAwareCacheResolver")
  public @NonNull Map<String, Object> getSalesAnalytics(
      @NonNull LocalDate from, @NonNull LocalDate to) {
    LocalDateTime fromDt = Objects.requireNonNull(from, "from date required").atStartOfDay();
    LocalDateTime toDt = Objects.requireNonNull(to, "to date required").atTime(LocalTime.MAX);

    BigDecimal totalRevenue =
        orderRepository.sumAmountByTypeAndDateRange(
            "SALE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));

    long totalOrders =
        orderRepository.countByTypeAndDateRange(
            "SALE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));
    BigDecimal avgOrderValue =
        totalOrders > 0
            ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

    Map<String, Object> analytics = new LinkedHashMap<>();
    analytics.put("period", Map.of("from", from, "to", to));
    analytics.put("total_revenue", totalRevenue);
    analytics.put("total_orders", totalOrders);
    analytics.put("average_order_value", avgOrderValue);
    analytics.put("cached_at", LocalDateTime.now().toString());

    return analytics;
  }

  public @NonNull Map<String, Object> getProfitLoss(
      @NonNull LocalDate from, @NonNull LocalDate to) {
    LocalDateTime fromDt = Objects.requireNonNull(from, "from date required").atStartOfDay();
    LocalDateTime toDt = Objects.requireNonNull(to, "to date required").atTime(LocalTime.MAX);

    BigDecimal salesRevenue =
        orderRepository.sumAmountByTypeAndDateRange(
            "SALE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));

    BigDecimal purchaseCost =
        orderRepository.sumAmountByTypeAndDateRange(
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

  public @NonNull Map<String, Object> getGstReport(@NonNull LocalDate from, @NonNull LocalDate to) {
    LocalDateTime fromDt = Objects.requireNonNull(from, "from date required").atStartOfDay();
    LocalDateTime toDt = Objects.requireNonNull(to, "to date required").atTime(LocalTime.MAX);

    BigDecimal totalSalesTax = orderRepository.sumTaxAmountByTypeAndDateRange("SALE", fromDt, toDt);

    BigDecimal totalPurchaseTax =
        orderRepository.sumTaxAmountByTypeAndDateRange("PURCHASE", fromDt, toDt);

    Map<String, Object> gst = new LinkedHashMap<>();
    gst.put("period", Map.of("from", from, "to", to));
    gst.put("output_gst", totalSalesTax);
    gst.put("input_gst", totalPurchaseTax);
    gst.put("net_gst_payable", totalSalesTax.subtract(totalPurchaseTax));

    return gst;
  }

  public @NonNull List<Map<String, Object>> getAlerts() {
    return alertRepository.findByTenantIdAndIsDismissedFalse(TenantContext.getTenantId()).stream()
        .map(
            a -> {
              Map<String, Object> map = new java.util.HashMap<>();
              map.put("id", a.getId());
              map.put("type", a.getType());
              map.put("severity", a.getSeverity());
              map.put("message", a.getMessage());
              map.put("resource_id", a.getResourceId());
              map.put("created_at", a.getCreatedAt());
              return map;
            })
        .collect(Collectors.toList());
  }

  @org.springframework.transaction.annotation.Transactional
  public void dismissAlert(@NonNull Long id) {
    alertRepository
        .findById(id)
        .ifPresent(
            a -> {
              if (a.getTenantId().equals(TenantContext.getTenantId())) {
                a.setIsDismissed(true);
                a.setDismissedAt(LocalDateTime.now());
                alertRepository.save(a);
              }
            });
  }

  private int statusPriority(@Nullable String status) {
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

  private String getBusinessType() {
    try {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
        return details.getBusinessType();
      }
    } catch (Exception e) {
      log.trace("Caught expected exception in report security context: {}", e.getMessage());
    }
    return null;
  }
}
