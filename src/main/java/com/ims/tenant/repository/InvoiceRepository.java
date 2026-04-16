package com.ims.tenant.repository;
 
import com.ims.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
 
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
 
  @Query(
      "SELECT COALESCE(MAX(CAST(SUBSTRING(i.invoiceNumber, LENGTH(i.invoiceNumber) - 3) AS int)), 0) "
          + "FROM Invoice i")
  int findMaxSequence();
 
  @Query("SELECT COUNT(i) > 0 FROM Invoice i WHERE i.orderId = :orderId")
  boolean existsByOrderId(@org.springframework.data.repository.query.Param("orderId") Long orderId);

  @Query("SELECT i FROM Invoice i JOIN Order o ON i.orderId = o.id WHERE o.customerId = :customerId")
  java.util.List<Invoice> findByCustomerId(@org.springframework.data.repository.query.Param("customerId") Long customerId);

  @Query("SELECT i FROM Invoice i JOIN Order o ON i.orderId = o.id WHERE o.supplierId = :supplierId")
  java.util.List<Invoice> findBySupplierId(@org.springframework.data.repository.query.Param("supplierId") Long supplierId);

  org.springframework.data.domain.Page<Invoice> findByStatusNotAndDueDateBefore(String status, java.time.LocalDate date, org.springframework.data.domain.Pageable pageable);
}
