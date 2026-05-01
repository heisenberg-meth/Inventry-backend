package com.ims.platform.repository;

import com.ims.model.SubscriptionPlan;
import com.ims.model.SubscriptionPlanStatus;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

  Optional<SubscriptionPlan> findByName(String name);

  boolean existsByName(String name);

  List<SubscriptionPlan> findByStatusOrderByCreatedAtDesc(SubscriptionPlanStatus status);

  List<SubscriptionPlan> findAllByOrderByCreatedAtDesc();

  @org.springframework.data.jpa.repository.Query("SELECT p FROM SubscriptionPlan p " +
      "WHERE p.status = com.ims.model.SubscriptionPlanStatus.ACTIVE " +
      "ORDER BY p.isDefault DESC, p.createdAt ASC")
  List<SubscriptionPlan> findPotentialDefaultPlans(org.springframework.data.domain.Pageable pageable);

  default Optional<SubscriptionPlan> findDefaultPlan() {
    List<SubscriptionPlan> plans = findPotentialDefaultPlans(org.springframework.data.domain.PageRequest.of(0, 1));
    return plans.isEmpty() ? Optional.empty() : Optional.of(plans.get(0));
  }
}
