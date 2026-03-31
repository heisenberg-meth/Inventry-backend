package com.ims.platform.repository;

import com.ims.model.SubscriptionPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

  Optional<SubscriptionPlan> findByName(String name);

  boolean existsByName(String name);

  List<SubscriptionPlan> findByStatusOrderByCreatedAtDesc(String status);

  List<SubscriptionPlan> findAllByOrderByCreatedAtDesc();
}
