package com.ims.tenant.repository;

import com.ims.model.Invoice;
import com.ims.model.InvoiceStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

  @Query(
      "SELECT COALESCE(MAX(CAST(SUBSTRING(i.invoiceNumber, LENGTH(i.invoiceNumber) - 3) AS int)), 0) "
          + "FROM Invoice i")
  int findMaxSequence();

  @Query("SELECT COUNT(i) > 0 FROM Invoice i WHERE i.orderId = :orderId")
  boolean existsByOrderId(@Param("orderId") Long orderId);

  @Query(
      "SELECT i FROM Invoice i JOIN Order o ON i.orderId = o.id WHERE o.customerId = :customerId")
  List<Invoice> findByCustomerId(@Param("customerId") Long customerId);

  @Query(
      "SELECT i FROM Invoice i JOIN Order o ON i.orderId = o.id WHERE o.supplierId = :supplierId")
  List<Invoice> findBySupplierId(@Param("supplierId") Long supplierId);

  org.springframework.data.domain.Page<Invoice> findAllByIsActiveTrue(org.springframework.data.domain.Pageable pageable);

  org.springframework.data.domain.Page<Invoice> findByStatusNotAndDueDateBefore(
      InvoiceStatus status, LocalDate date, org.springframework.data.domain.Pageable pageable);

  @Query("SELECT i FROM Invoice i WHERE i.status <> :status AND i.dueDate < :date")
  Stream<Invoice> streamOverdue(@Param("status") InvoiceStatus status, @Param("date") LocalDate date);

  @Query("SELECT i FROM Invoice i WHERE i.status <> :status AND i.dueDate < :date")
  Stream<Invoice> streamAllOverdueGlobal(@Param("status") InvoiceStatus status, @Param("date") LocalDate date);
}
