package com.ims.tenant.service;

import com.ims.model.Order;
import com.ims.product.Product;
import com.ims.product.ProductRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantAnalyticsService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ReportService reportService;

    public List<Map<String, Object>> getRevenueTrend() {
        List<Order> sales = orderRepository.findByType("SALE", org.springframework.data.domain.Pageable.unpaged()).getContent();
        
        // Group by month
        Map<String, BigDecimal> monthlyRevenue = sales.stream()
            .collect(Collectors.groupingBy(
                o -> o.getCreatedAt().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                LinkedHashMap::new,
                Collectors.reducing(BigDecimal.ZERO, Order::getTotalAmount, BigDecimal::add)
            ));

        return monthlyRevenue.entrySet().stream()
            .map(e -> Map.<String, Object>of("month", e.getKey(), "revenue", e.getValue()))
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTopProducts() {
        // Mocking for now based on total products or simplified logic
        return productRepository.findByIsActiveTrue(org.springframework.data.domain.PageRequest.of(0, 5)).getContent().stream()
            .map(p -> Map.<String, Object>of("name", p.getName(), "value", p.getStock() * 100)) // Replace with real sales data if available
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getOrderStatusStats() {
        List<Order> orders = orderRepository.findAll();
        long total = orders.size();
        if (total == 0) return Collections.emptyList();

        Map<String, Long> counts = orders.stream()
            .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));

        return counts.entrySet().stream()
            .map(e -> Map.<String, Object>of(
                "label", e.getKey(),
                "count", e.getValue(),
                "pct", (double) e.getValue() / total * 100
            ))
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getQuickStats() {
        Map<String, Object> dashboard = reportService.getDashboard();
        return List.of(
            Map.of("label", "Total Revenue", "value", dashboard.getOrDefault("today_sales_amount", 0), "highlight", true),
            Map.of("label", "Active Products", "value", dashboard.getOrDefault("total_products", 0), "highlight", false),
            Map.of("label", "Low Stock", "value", dashboard.getOrDefault("low_stock_count", 0), "highlight", true)
        );
    }

    public Map<String, Object> getAiHealth() {
        Long tenantId = TenantContext.getTenantId();
        long total = productRepository.countActiveByTenant(tenantId);
        long lowStock = productRepository.countLowStockByTenant(tenantId);
        double score = total == 0 ? 100 : Math.max(0, 100 - ((double) lowStock / total * 100));

        Map<String, Object> health = new HashMap<>();
        health.put("score", score);
        health.put("metrics", List.of(
                Map.<String, Object>of("label", "Inventory Accuracy", "pct", 98),
                Map.<String, Object>of("label", "Order Fulfillment", "pct", 95)
            ));
        health.put("realtimeCount", 12);
        health.put("predictiveCount", 5);
        health.put("errorCount", 0);
        health.put("pricingCount", 3);
        health.put("predictedRestocks", (int) lowStock);
        health.put("demandSurgeItems", 2);
        health.put("accuracyScore", "94%");
        health.put("anomaliesDetected", 1);
        health.put("autoResolved", 0);
        health.put("pendingReview", 1);
        return health;
    }

    public List<Map<String, Object>> getAiRecommendations() {
        List<Product> lowStock = productRepository.findLowStock(TenantContext.getTenantId());
        return lowStock.stream()
            .map(p -> Map.<String, Object>of(
                "id", p.getId(),
                "tag", "Inventory",
                "title", "Low Stock: " + p.getName(),
                "desc", "Stock level is at " + p.getStock() + ". Reorder recommended.",
                "time", "1h ago",
                "urgency", "critical"
            ))
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getAiDemandForecast() {
        return List.of(
            Map.<String, Object>of("product", "Example Product A", "change", "+15%", "status", "Surge"),
            Map.<String, Object>of("product", "Example Product B", "change", "-5%", "status", "Stable")
        );
    }

    public List<Map<String, Object>> getAiAnomalies() {
        return List.of(
            Map.<String, Object>of("title", "Price Variance", "desc", "Sudden increase in purchase price for SKU-123", "time", "2h ago", "status", "Pending", "severity", "warning")
        );
    }
}
