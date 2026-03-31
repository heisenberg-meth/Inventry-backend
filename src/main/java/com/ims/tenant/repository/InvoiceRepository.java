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
 
  boolean existsByOrderId(Long orderId);
}
