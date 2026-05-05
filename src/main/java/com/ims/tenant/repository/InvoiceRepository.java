package com.ims.tenant.repository;

import com.ims.model.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

  @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.orderId = :orderId")
  Optional<Invoice> findByTenantIdAndOrderId(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);

  @Query("SELECT i FROM Invoice i WHERE i.id = :id AND i.tenantId = :tenantId")
  Optional<Invoice> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

  @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.orderId = :orderId")
  Optional<Invoice> findByOrderId(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);

  @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.orderId = :orderId")
  boolean existsByTenantIdAndOrderId(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);

  @Query("SELECT i FROM Invoice i JOIN Order o ON i.orderId = o.id WHERE i.tenantId = :tenantId AND o.customerId = :customerId")
  List<Invoice> findByTenantIdAndCustomerId(@Param("tenantId") Long tenantId, @Param("customerId") Long customerId);

  @Query("SELECT i FROM Invoice i JOIN Order o ON i.orderId = o.id WHERE i.tenantId = :tenantId AND o.supplierId = :supplierId")
  List<Invoice> findByTenantIdAndSupplierId(@Param("tenantId") Long tenantId, @Param("supplierId") Long supplierId);

  @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.status != :status AND i.dueDate < :date")
  Page<Invoice> findByTenantIdAndStatusNotAndDueDateBefore(@Param("tenantId") Long tenantId,
      @Param("status") String status, @Param("date") java.time.LocalDate date, Pageable pageable);

  @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId")
  Page<Invoice> findAllByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

}
