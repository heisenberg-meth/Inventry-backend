package com.ims.tenant.repository;

import com.ims.model.TransferOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferOrderRepository extends JpaRepository<TransferOrder, Long> {
  // findAll and findById are inherited and automatically filtered by @TenantId
}
