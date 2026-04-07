package com.ims.platform.repository;

import com.ims.model.Subscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

  List<Subscription> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

  Optional<Subscription> findFirstByTenantIdOrderByCreatedAtDesc(Long tenantId);

  List<Subscription> findByTenantIdAndStatus(Long tenantId, String status);

  long countByPlanAndStatus(String plan, String status);
}
