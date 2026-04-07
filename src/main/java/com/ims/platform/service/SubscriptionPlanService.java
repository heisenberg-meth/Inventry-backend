package com.ims.platform.service;

import com.ims.dto.request.CreateSubscriptionPlanRequest;
import com.ims.dto.request.UpdateSubscriptionPlanRequest;
import com.ims.model.SubscriptionPlan;
import com.ims.platform.repository.SubscriptionPlanRepository;
import com.ims.platform.repository.SubscriptionRepository;
import com.ims.shared.audit.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.Objects;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanService {

  private final SubscriptionPlanRepository planRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final AuditLogService auditLogService;
  private final ObjectMapper objectMapper;

  @Transactional
  public SubscriptionPlan createPlan(@NonNull CreateSubscriptionPlanRequest request) {
    if (planRepository.existsByName(request.getName())) {
      throw new IllegalArgumentException("Plan name already exists: " + request.getName());
    }

    String features = null;
    if (request.getFeatures() != null) {
      try {
        features = objectMapper.writeValueAsString(request.getFeatures());
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid features JSON");
      }
    }

    SubscriptionPlan plan =
        SubscriptionPlan.builder()
            .name(request.getName())
            .price(request.getPrice())
            .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
            .billingCycle(request.getBillingCycle())
            .features(features)
            .maxUsers(request.getMaxUsers())
            .maxProducts(request.getMaxProducts())
            .status("ACTIVE")
            .build();

    @SuppressWarnings("null")
    SubscriptionPlan saved = Objects.requireNonNull(planRepository.save(plan));
    auditLogService.log("CREATE_PLAN", null, null, "Created plan: " + saved.getName());
    log.info("Subscription plan created: id={} name={}", saved.getId(), saved.getName());
    return saved;
  }

  @Transactional(readOnly = true)
  public List<SubscriptionPlan> findAll(String status) {
    if (status != null && !status.isBlank()) {
      return planRepository.findByStatusOrderByCreatedAtDesc(status);
    }
    return planRepository.findAllByOrderByCreatedAtDesc();
  }

  @Transactional(readOnly = true)
  public SubscriptionPlan findOne(@NonNull Long id) {
    return planRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Subscription plan not found"));
  }

  @Transactional
  public SubscriptionPlan updatePlan(
      @NonNull Long id, @NonNull UpdateSubscriptionPlanRequest request) {
    SubscriptionPlan plan = findOne(id);

    if (request.getName() != null) {
      plan.setName(request.getName());
    }
    if (request.getPrice() != null) {
      plan.setPrice(request.getPrice());
    }
    if (request.getFeatures() != null) {
      try {
        plan.setFeatures(objectMapper.writeValueAsString(request.getFeatures()));
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid features JSON");
      }
    }
    if (request.getMaxUsers() != null) {
      plan.setMaxUsers(request.getMaxUsers());
    }
    if (request.getMaxProducts() != null) {
      plan.setMaxProducts(request.getMaxProducts());
    }

    plan.setVersion(plan.getVersion() + 1);
    plan.setUpdatedAt(LocalDateTime.now());
    return planRepository.save(plan);
  }

  @Transactional
  public Map<String, String> deletePlan(@NonNull Long id) {
    SubscriptionPlan plan = findOne(id);
    plan.setStatus("DELETED");
    plan.setUpdatedAt(LocalDateTime.now());
    planRepository.save(plan);

    auditLogService.log("DELETE_PLAN", null, null, "Deleted plan: " + plan.getName());
    return Map.of("message", "Plan soft-deleted", "plan", plan.getName());
  }

  @Transactional
  public Map<String, String> activatePlan(@NonNull Long id) {
    SubscriptionPlan plan = findOne(id);
    plan.setStatus("ACTIVE");
    plan.setUpdatedAt(LocalDateTime.now());
    planRepository.save(plan);
    return Map.of("message", "Plan activated", "status", "ACTIVE");
  }

  @Transactional
  public Map<String, String> deactivatePlan(@NonNull Long id) {
    SubscriptionPlan plan = findOne(id);
    plan.setStatus("INACTIVE");
    plan.setUpdatedAt(LocalDateTime.now());
    planRepository.save(plan);
    return Map.of("message", "Plan deactivated", "status", "INACTIVE");
  }

  /**
   * Get usage summary with MRR/ARR metrics.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> getUsageSummary() {
    List<SubscriptionPlan> allPlans = planRepository.findAllByOrderByCreatedAtDesc();

    BigDecimal mrr = BigDecimal.ZERO;
    long totalActive = 0;
    Map<String, Long> planBreakdown = new LinkedHashMap<>();

    for (SubscriptionPlan plan : allPlans) {
      if (!"ACTIVE".equals(plan.getStatus())) {
        continue;
      }
      long activeCount = subscriptionRepository.countByPlanAndStatus(plan.getName(), "ACTIVE");
      planBreakdown.put(plan.getName(), activeCount);
      totalActive += activeCount;

      if (plan.getPrice() != null) {
        mrr = mrr.add(plan.getPrice().multiply(BigDecimal.valueOf(activeCount)));
      }
    }

    Map<String, Object> summary = new HashMap<>();
    summary.put("totalPlans", allPlans.size());
    summary.put("activePlans", allPlans.stream().filter(p -> "ACTIVE".equals(p.getStatus())).count());
    summary.put("totalActiveSubscriptions", totalActive);
    summary.put("planBreakdown", planBreakdown);
    summary.put("mrr", mrr);
    summary.put("arr", mrr.multiply(BigDecimal.valueOf(12)));
    return summary;
  }
}
