package com.ims.tenant.repository;

import com.ims.model.TransferOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferOrderRepository extends JpaRepository<TransferOrder, Long> {
    @Query("SELECT t FROM TransferOrder t WHERE t.tenantId = :tenantId")
    Page<TransferOrder> findAllByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

    @Query("SELECT t FROM TransferOrder t WHERE t.id = :id AND t.tenantId = :tenantId")
    java.util.Optional<TransferOrder> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
