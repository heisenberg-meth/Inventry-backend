package com.ims.tenant.service;

import com.ims.product.ProductRepository;
import com.ims.tenant.repository.OrderRepository;
import java.util.Objects;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantAnalyticsService {

  private static final int REVENUE_TREND_WINDOW_MONTHS = 12;
  private static final int ORDER_STATUS_WINDOW_MONTHS = 6;
  private static final int TOP_PRODUCTS_LIMIT = 5;
  private static final int PERCENT_MULTIPLIER = 100;
  private static final int PLACEHOLDER_TOP_PRODUCT_VALUE_MULTIPLIER = 100;

  @org.springframework.beans.factory.annotation.Value("${feature.ai.enabled:false}")
  private boolean aiEnabled;

  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final ReportService reportService;

  public List<Map<String, Object>> getRevenueTrend() {
    LocalDateTime from = LocalDateTime.now()
        .minusMonths(REVENUE_TREND_WINDOW_MONTHS)
        .withDayOfMonth(1)
        .withHour(0)
        .withMinute(0);

    return Objects.requireNonNull(
        orderRepository.getMonthlyRevenue("SALE", from).stream()
            .map(
                r -> {
                  String monthName = Month.of(r.getMonth()).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                  Map<String, Object> map = new LinkedHashMap<>();
                  map.put("month", monthName + " " + r.getYear());
                  map.put("revenue", r.getRevenue());
                  return map;
                })
            .collect(Collectors.toList()));
  }

  public List<Map<String, Object>> getTopProducts() {
    return Objects.requireNonNull(
        productRepository
            .findTopStock(
                org.springframework.data.domain.PageRequest.of(0, TOP_PRODUCTS_LIMIT))
            .getContent()
            .stream()
            .map(
                p -> Map.<String, Object>of(
                    "name",
                    p.getName(),
                    "value",
                    p.getStock() * PLACEHOLDER_TOP_PRODUCT_VALUE_MULTIPLIER))
            .collect(Collectors.toList()));
  }

  public List<Map<String, Object>> getOrderStatusStats() {
    LocalDateTime from = LocalDateTime.now().minusMonths(ORDER_STATUS_WINDOW_MONTHS);

    var stats = orderRepository.getOrderStatusStats(from);
    long total = stats.stream().mapToLong(com.ims.tenant.dto.OrderStatusStat::getCount).sum();

    if (total == 0) {
      return Objects.requireNonNull(Collections.emptyList());
    }

    return Objects.requireNonNull(
        stats.stream()
            .map(
                s -> {
                  Map<String, Object> map = new LinkedHashMap<>();
                  map.put("label", s.getStatus());
                  map.put("count", s.getCount());
                  map.put("pct", (double) s.getCount() / total * PERCENT_MULTIPLIER);
                  return map;
                })
            .collect(Collectors.toList()));
  }

  public List<Map<String, Object>> getQuickStats() {
    Map<String, Object> dashboard = Objects.requireNonNull(reportService.getDashboard());
    return Objects.requireNonNull(
        List.of(
            Map.of(
                "label",
                "Total Revenue",
                "value",
                dashboard.getOrDefault("today_sales_amount", 0),
                "highlight",
                true),
            Map.of(
                "label",
                "Active Products",
                "value",
                dashboard.getOrDefault("total_products", 0),
                "highlight",
                false),
            Map.of(
                "label",
                "Low Stock",
                "value",
                dashboard.getOrDefault("low_stock_count", 0),
                "highlight",
                true)));
  }

  private void checkAiEnabled() {
    if (!aiEnabled) {
      throw new UnsupportedOperationException("AI features are currently disabled");
    }
    throw new UnsupportedOperationException("AI pipeline not yet implemented");
  }

  public Map<String, Object> getAiHealth() {
    checkAiEnabled();
    return Objects.requireNonNull(Collections.emptyMap());
  }

  public List<Map<String, Object>> getAiRecommendations() {
    checkAiEnabled();
    return Objects.requireNonNull(Collections.emptyList());
  }

  public List<Map<String, Object>> getAiDemandForecast() {
    checkAiEnabled();
    return Objects.requireNonNull(Collections.emptyList());
  }

  public List<Map<String, Object>> getAiAnomalies() {
    checkAiEnabled();
    return Objects.requireNonNull(Collections.emptyList());
  }
}
