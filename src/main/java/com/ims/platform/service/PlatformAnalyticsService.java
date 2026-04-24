package com.ims.platform.service;

import com.ims.platform.repository.SubscriptionPlanRepository;
import com.ims.platform.repository.SubscriptionRepository;
import com.ims.platform.repository.TenantRepository;
import com.ims.tenant.repository.UserRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAnalyticsService {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionPlanRepository subscriptionPlanRepository;

  public Map<String, Object> getRevenueAnalytics() {
    var activeSubscriptions = subscriptionRepository.findByStatus("ACTIVE");
    var plans =
        subscriptionPlanRepository.findAll().stream()
            .collect(Collectors.toMap(com.ims.model.SubscriptionPlan::getName, p -> p));

    BigDecimal mrr = BigDecimal.ZERO;
    for (var sub : activeSubscriptions) {
      var plan = plans.get(sub.getPlan());
      if (plan != null && plan.getPrice() != null) {
        // Simplified MRR: assumes monthly cycle. For yearly, should divide by 12.
        mrr = mrr.add(plan.getPrice());
      }
    }

    Map<String, Object> revenue = new LinkedHashMap<>();
    revenue.put("mrr", mrr);
    revenue.put("arr", mrr.multiply(BigDecimal.valueOf(12)));
    revenue.put("active_subscriptions", activeSubscriptions.size());

    return revenue;
  }

  public Map<String, Object> getTenantAnalytics() {
    var tenants = tenantRepository.findAll();

    // Simplified trend: grouped by month of creation
    var trend =
        tenants.stream()
            .collect(
                Collectors.groupingBy(
                    t -> t.getCreatedAt().getMonth().name(), Collectors.counting()));

    Map<String, Object> analytics = new LinkedHashMap<>();
    analytics.put("total_tenants", tenants.size());
    analytics.put(
        "active_tenants", tenants.stream().filter(t -> "ACTIVE".equals(t.getStatus())).count());
    analytics.put("signup_trend", trend);

    return analytics;
  }

  public Map<String, Object> getUsageAnalytics() {
    long totalUsers = userRepository.count();
    long activeUsers = userRepository.countActive();

    Map<String, Object> usage = new LinkedHashMap<>();
    usage.put("total_users", totalUsers);
    usage.put("active_users", activeUsers);
    usage.put(
        "avg_users_per_tenant",
        totalUsers > 0 ? (double) totalUsers / tenantRepository.count() : 0);

    return usage;
  }
}
