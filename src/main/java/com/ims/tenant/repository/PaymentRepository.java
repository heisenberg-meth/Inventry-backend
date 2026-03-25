package com.ims.tenant.repository;

import com.ims.model.Payment;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
  Page<Payment> findByTenantId(Long tenantId, Pageable pageable);

  Optional<Payment> findByIdAndTenantId(Long id, Long tenantId);

  @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.invoiceId = :invoiceId")
  BigDecimal sumAmountByInvoiceId(@Param("invoiceId") Long invoiceId);
}
