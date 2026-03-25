package com.ims.tenant.repository;

import com.ims.model.TransferOrder;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferOrderRepository extends JpaRepository<TransferOrder, Long> {
  Page<TransferOrder> findByTenantId(Long tenantId, Pageable pageable);

  Optional<TransferOrder> findByIdAndTenantId(Long id, Long tenantId);
}
