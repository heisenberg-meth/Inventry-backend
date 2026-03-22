package com.ims.tenant.repository;

import com.ims.model.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByIdAndTenantId(Long id, Long tenantId);
    Page<Invoice> findByTenantId(Long tenantId, Pageable pageable);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(i.invoiceNumber, LENGTH(i.invoiceNumber) - 3) AS int)), 0) FROM Invoice i WHERE i.tenantId = :tenantId")
    int findMaxSequenceByTenantId(@Param("tenantId") Long tenantId);
}
