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
}
