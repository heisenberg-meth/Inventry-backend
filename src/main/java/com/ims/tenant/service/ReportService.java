package com.ims.tenant.service;

import com.ims.shared.auth.JwtAuthDetails;
import com.ims.tenant.domain.pharmacy.PharmacyProductRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PharmacyProductRepository pharmacyProductRepository;

    @Cacheable(value = "reports", key = "#tenantId + ':dashboard'")
    public Map<String, Object> getDashboard(Long tenantId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

        long totalProducts = productRepository.countActiveByTenantId(tenantId);
        long lowStockCount = productRepository.countLowStockByTenantId(tenantId);
        long outOfStockCount = productRepository.countOutOfStockByTenantId(tenantId);

        BigDecimal todaySalesAmount = orderRepository.sumAmountByTenantIdAndTypeAndDateRange(
                tenantId, "SALE", todayStart, todayEnd);
        long todaySalesCount = orderRepository.countByTenantIdAndTypeAndDateRange(
                tenantId, "SALE", todayStart, todayEnd);
        BigDecimal todayPurchasesAmount = orderRepository.sumAmountByTenantIdAndTypeAndDateRange(
                tenantId, "PURCHASE", todayStart, todayEnd);

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
            LocalDate threshold = LocalDate.now().plusDays(30);
            long expiringSoon = pharmacyProductRepository.countExpiringByTenantId(tenantId, threshold);
            dashboard.put("expiring_soon_count", expiringSoon);
        }

        dashboard.put("cached_at", LocalDateTime.now().toString());
        return dashboard;
    }

    @Cacheable(value = "reports", key = "#tenantId + ':stock-report'")
    public List<Map<String, Object>> getStockReport(Long tenantId, String filter) {
        var products = productRepository.findByTenantIdAndIsActiveTrue(tenantId, Pageable.unpaged()).getContent();
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
                var pp = pharmacyProductRepository.findById(product.getId());
                if (pp.isPresent()) {
                    expiryDate = pp.get().getExpiryDate();
                    if (expiryDate != null && expiryDate.isBefore(LocalDate.now().plusDays(30))) {
                        status = "EXPIRING";
                    }
                }
            } catch (Exception ignored) {}

            // Apply filter
            if ("low".equals(filter) && !"LOW".equals(status) && !"OUT_OF_STOCK".equals(status)) continue;
            if ("expiring".equals(filter) && !"EXPIRING".equals(status)) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("product_id", product.getId());
            item.put("product_name", product.getName());
            item.put("sku", product.getSku());
            item.put("current_stock", product.getStock());
            item.put("reorder_level", product.getReorderLevel());
            item.put("unit", product.getUnit());
            item.put("status", status);
            if (expiryDate != null) item.put("expiry_date", expiryDate);
            report.add(item);
        }

        // Sort by urgency: OUT_OF_STOCK > LOW > EXPIRING > OK
        report.sort((a, b) -> {
            int priority = statusPriority((String) a.get("status")) - statusPriority((String) b.get("status"));
            return priority;
        });

        return report;
    }

    @Cacheable(value = "reports", key = "#tenantId + ':sales:' + #from + ':' + #to")
    public Map<String, Object> getSalesAnalytics(Long tenantId, LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(LocalTime.MAX);

        BigDecimal totalRevenue = orderRepository.sumAmountByTenantIdAndTypeAndDateRange(tenantId, "SALE", fromDt, toDt);
        long totalOrders = orderRepository.countByTenantIdAndTypeAndDateRange(tenantId, "SALE", fromDt, toDt);
        BigDecimal avgOrderValue = totalOrders > 0
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

    public Map<String, Object> getProfitLoss(Long tenantId, LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(LocalTime.MAX);

        BigDecimal salesRevenue = orderRepository.sumAmountByTenantIdAndTypeAndDateRange(tenantId, "SALE", fromDt, toDt);
        BigDecimal purchaseCost = orderRepository.sumAmountByTenantIdAndTypeAndDateRange(tenantId, "PURCHASE", fromDt, toDt);
        BigDecimal profit = salesRevenue.subtract(purchaseCost);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("period", Map.of("from", from, "to", to));
        report.put("total_sales", salesRevenue);
        report.put("total_purchases", purchaseCost);
        report.put("gross_profit", profit);
        report.put("margin_percentage", salesRevenue.compareTo(BigDecimal.ZERO) > 0
                ? profit.multiply(BigDecimal.valueOf(100)).divide(salesRevenue, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        return report;
    }

    private int statusPriority(String status) {
        return switch (status) {
            case "OUT_OF_STOCK" -> 0;
            case "LOW" -> 1;
            case "EXPIRING" -> 2;
            default -> 3;
        };
    }

    private String getBusinessType() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
                return details.getBusinessType();
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }
}
