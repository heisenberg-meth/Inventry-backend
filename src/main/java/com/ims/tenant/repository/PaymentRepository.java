package com.ims.tenant.repository;

import com.ims.model.Payment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    // findAll and findById are inherited
    @Query("SELECT p FROM Payment p WHERE p.id = :id AND p.tenantId = :tenantId")
    Optional<Payment> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Query("SELECT p FROM Payment p WHERE p.tenantId = :tenantId")
    org.springframework.data.domain.Page<Payment> findAllByTenantId(@Param("tenantId") Long tenantId,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p FROM Payment p JOIN Invoice i ON p.invoiceId = i.id "
            + "JOIN Order o ON i.orderId = o.id WHERE o.customerId = :customerId AND p.tenantId = :tenantId")
    List<Payment> findByCustomerIdAndTenantId(@Param("customerId") Long customerId, @Param("tenantId") Long tenantId);

    @Query("SELECT p FROM Payment p JOIN Invoice i ON p.invoiceId = i.id "
            + "JOIN Order o ON i.orderId = o.id WHERE o.supplierId = :supplierId AND p.tenantId = :tenantId")
    List<Payment> findBySupplierIdAndTenantId(@Param("supplierId") Long supplierId, @Param("tenantId") Long tenantId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.invoiceId = :invoiceId")
    BigDecimal sumAmountByInvoiceId(@Param("invoiceId") Long invoiceId);

    Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId);
}
