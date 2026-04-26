package com.ims.platform.repository;

import com.ims.model.Subscription;
import com.ims.model.SubscriptionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

  List<Subscription> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

  Optional<Subscription> findFirstByTenantIdOrderByCreatedAtDesc(Long tenantId);

  List<Subscription> findByTenantIdAndStatus(Long tenantId, SubscriptionStatus status);

  List<Subscription> findByStatus(SubscriptionStatus status);

  long countByPlanAndStatus(String plan, SubscriptionStatus status);
}
