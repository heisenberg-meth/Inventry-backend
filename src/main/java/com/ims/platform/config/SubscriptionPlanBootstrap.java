package com.ims.platform.config;

import com.ims.model.SubscriptionPlan;
import com.ims.model.SubscriptionPlanStatus;
import com.ims.platform.repository.SubscriptionPlanRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanBootstrap {

  private final SubscriptionPlanRepository repo;

  @PostConstruct
  public void init() {
    if (repo.findDefaultPlan().isEmpty()) {
      log.info("No default subscription plan found. Seeding DEFAULT plan...");
      SubscriptionPlan plan = SubscriptionPlan.builder()
          .name("DEFAULT")
          .status(SubscriptionPlanStatus.ACTIVE)
          .isDefault(true)
          .billingCycle("MONTHLY")
          .build();
      repo.save(plan);
      log.info("DEFAULT subscription plan seeded successfully.");
    }
  }
}
