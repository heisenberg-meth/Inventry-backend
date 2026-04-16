package com.ims.tenant.repository;

import com.ims.model.Payment;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
  // findAll and findById are inherited

  @Query("SELECT p FROM Payment p JOIN Invoice i ON p.invoiceId = i.id JOIN Order o ON i.orderId = o.id WHERE o.customerId = :customerId")
  java.util.List<Payment> findByCustomerId(@Param("customerId") Long customerId);

  @Query("SELECT p FROM Payment p JOIN Invoice i ON p.invoiceId = i.id JOIN Order o ON i.orderId = o.id WHERE o.supplierId = :supplierId")
  java.util.List<Payment> findBySupplierId(@Param("supplierId") Long supplierId);

  @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.invoiceId = :invoiceId")
  BigDecimal sumAmountByInvoiceId(@Param("invoiceId") Long invoiceId);
}
