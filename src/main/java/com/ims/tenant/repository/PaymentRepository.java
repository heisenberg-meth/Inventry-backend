package com.ims.tenant.repository;

import com.ims.model.Payment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

  @Query("SELECT p FROM Payment p WHERE p.tenantId = :tenantId AND p.id = :id")
  java.util.Optional<Payment> findByIdAndTenantId(
      @Param("tenantId") Long tenantId, @Param("id") Long id);

  @Query("SELECT p FROM Payment p WHERE p.tenantId = :tenantId")
  Page<Payment> findAllByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

  @Query(
      "SELECT p FROM Payment p JOIN Invoice i ON p.invoiceId = i.id "
          + "JOIN Order o ON i.orderId = o.id WHERE p.tenantId = :tenantId AND o.customerId = :customerId")
  List<Payment> findByTenantIdAndCustomerId(
      @Param("tenantId") Long tenantId, @Param("customerId") Long customerId);

  @Query(
      "SELECT p FROM Payment p JOIN Invoice i ON p.invoiceId = i.id "
          + "JOIN Order o ON i.orderId = o.id WHERE p.tenantId = :tenantId AND o.supplierId = :supplierId")
  List<Payment> findByTenantIdAndSupplierId(
      @Param("tenantId") Long tenantId, @Param("supplierId") Long supplierId);

  @Query(
      "SELECT COALESCE(SUM(p.amount), 0) FROM Payment p "
          + "WHERE p.tenantId = :tenantId AND p.invoiceId = :invoiceId "
          + "AND (p.status = 'COMPLETED' OR p.status = 'SUCCESS')")
  BigDecimal sumAmountByTenantIdAndInvoiceId(
      @Param("tenantId") Long tenantId, @Param("invoiceId") Long invoiceId);

  @Query(
      "SELECT p FROM Payment p WHERE p.tenantId = :tenantId "
          + "AND p.gatewayTransactionId = :gatewayTransactionId")
  Optional<Payment> findByTenantIdAndGatewayTransactionId(
      @Param("tenantId") Long tenantId, @Param("gatewayTransactionId") String gatewayTransactionId);

  Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId);

  @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.invoiceId = :invoiceId")
  BigDecimal sumAmountByInvoiceId(@Param("invoiceId") Long invoiceId);
}
