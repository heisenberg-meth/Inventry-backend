package com.ims.tenant.service;

import com.ims.shared.auth.JwtAuthDetails;
import com.ims.tenant.domain.pharmacy.PharmacyProductRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.ProductRepository;
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
 
  private static final int DEFAULT_DAYS = 30;
  private static final int PERCENTAGE_BASE = 100;
  private static final int STATUS_PRIORITY_OK = 3;

  @Cacheable(value = "reports", key = "T(com.ims.shared.auth.TenantContext).get() + ':purchases:' + #from + ':' + #to")
  public @NonNull Map<String, Object> getPurchasesReport(@NonNull LocalDate from, @NonNull LocalDate to) {
    LocalDateTime fromDt = Objects.requireNonNull(from).atStartOfDay();
    LocalDateTime toDt = Objects.requireNonNull(to).atTime(LocalTime.MAX);

    BigDecimal totalSpent =
        Objects.requireNonNull(orderRepository.sumAmountByTypeAndDateRange("PURCHASE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt)));
    long totalOrders =
        orderRepository.countByTypeAndDateRange("PURCHASE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));
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

  @Cacheable(value = "reports", key = "T(com.ims.shared.auth.TenantContext).get() + ':dashboard'")
  public @NonNull Map<String, Object> getDashboard() {
    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

    long totalProducts = productRepository.countActive();
    long lowStockCount = productRepository.countLowStock();
    long outOfStockCount = productRepository.countOutOfStock();

    BigDecimal todaySalesAmount =
        Objects.requireNonNull(orderRepository.sumAmountByTypeAndDateRange(
            "SALE", Objects.requireNonNull(todayStart), Objects.requireNonNull(todayEnd)));
    long todaySalesCount =
        orderRepository.countByTypeAndDateRange("SALE", Objects.requireNonNull(todayStart), Objects.requireNonNull(todayEnd));
    BigDecimal todayPurchasesAmount =
        Objects.requireNonNull(orderRepository.sumAmountByTypeAndDateRange(
            "PURCHASE", Objects.requireNonNull(todayStart), Objects.requireNonNull(todayEnd)));

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
      LocalDate threshold = LocalDate.now().plusDays(DEFAULT_DAYS);
      long expiringSoon = pharmacyProductRepository.countExpiring(threshold);
      dashboard.put("expiring_soon_count", expiringSoon);
    }

    dashboard.put("cached_at", LocalDateTime.now().toString());
    return dashboard;
  }

  @Cacheable(value = "reports", key = "T(com.ims.shared.auth.TenantContext).get() + ':stock-report'")
  public @NonNull List<Map<String, Object>> getStockReport(@Nullable String filter) {
    var products =
        Objects.requireNonNull(productRepository.findByIsActiveTrue(Pageable.unpaged())).getContent();
    List<Map<String, Object>> report = new ArrayList<>();

    for (var product : products) {
      String status;
      if (product.getStock() == 0) {
        status = "OUT_OF_STOCK";
      } else if (product.getStock() <= product.getReorderLevel()) {
        status = "LOW";
      } else {
        status = "OK";
      }

      // Check expiry for pharmacy
      LocalDate expiryDate = null;
      try {
        var pp = pharmacyProductRepository.findById(Objects.requireNonNull(product.getId()));
        if (pp.isPresent()) {
          expiryDate = pp.get().getExpiryDate();
          if (expiryDate != null && expiryDate.isBefore(LocalDate.now().plusDays(DEFAULT_DAYS))) {
            status = "EXPIRING";
          }
        }
      } catch (Exception e) {
        log.trace("Caught expected exception for non-pharmacy product: {}", e.getMessage());
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

  @Cacheable(value = "reports", key = "T(com.ims.shared.auth.TenantContext).get() + ':sales:' + #from + ':' + #to")
  public @NonNull Map<String, Object> getSalesAnalytics(@NonNull LocalDate from, @NonNull LocalDate to) {
    LocalDateTime fromDt = Objects.requireNonNull(from).atStartOfDay();
    LocalDateTime toDt = Objects.requireNonNull(to).atTime(LocalTime.MAX);

    BigDecimal totalRevenue =
        Objects.requireNonNull(orderRepository.sumAmountByTypeAndDateRange("SALE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt)));
    long totalOrders =
        orderRepository.countByTypeAndDateRange("SALE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt));
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

  public @NonNull Map<String, Object> getProfitLoss(@NonNull LocalDate from, @NonNull LocalDate to) {
    LocalDateTime fromDt = Objects.requireNonNull(from).atStartOfDay();
    LocalDateTime toDt = Objects.requireNonNull(to).atTime(LocalTime.MAX);

    BigDecimal salesRevenue =
        Objects.requireNonNull(orderRepository.sumAmountByTypeAndDateRange("SALE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt)));
    BigDecimal purchaseCost =
        Objects.requireNonNull(orderRepository.sumAmountByTypeAndDateRange("PURCHASE", Objects.requireNonNull(fromDt), Objects.requireNonNull(toDt)));
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

  private int statusPriority(@Nullable String status) {
    if (status == null) return STATUS_PRIORITY_OK;
    return switch (status) {
      case "OUT_OF_STOCK" -> 0;
      case "LOW" -> 1;
      case "EXPIRING" -> 2;
      default -> STATUS_PRIORITY_OK;
    };
  }

  private @Nullable String getBusinessType() {
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
