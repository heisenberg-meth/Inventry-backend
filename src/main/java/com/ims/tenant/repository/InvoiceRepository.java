package com.ims.tenant.repository;

import com.ims.model.Invoice;
import com.ims.model.InvoiceStatus;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
        @Query("SELECT i FROM Invoice i WHERE i.id = :id AND i.tenantId = :tenantId")
        Optional<Invoice> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

        @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(i.invoiceNumber, LENGTH(i.invoiceNumber) - 3) AS int)), 0) "
                        + "FROM Invoice i")
        int findMaxSequence();

        @Query("SELECT COUNT(i) > 0 FROM Invoice i WHERE i.orderId = :orderId")
        boolean existsByOrderId(@Param("orderId") Long orderId);

        @Query("SELECT i FROM Invoice i JOIN Order o ON i.orderId = o.id WHERE o.customerId = :customerId AND i.tenantId = :tenantId")
        List<Invoice> findByCustomerIdAndTenantId(@Param("customerId") Long customerId,
                        @Param("tenantId") Long tenantId);

        @Query("SELECT i FROM Invoice i JOIN Order o ON i.orderId = o.id WHERE o.supplierId = :supplierId AND i.tenantId = :tenantId")
        List<Invoice> findBySupplierIdAndTenantId(@Param("supplierId") Long supplierId,
                        @Param("tenantId") Long tenantId);

        Page<Invoice> findAllByActiveTrue(Pageable pageable);

        @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId")
        Page<Invoice> findAllByTenantId(@Param("tenantId") Long tenantId,
                        Pageable pageable);

        @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.status <> :status AND i.dueDate < :date")
        Page<Invoice> findOverdueByTenantId(@Param("tenantId") Long tenantId,
                        @Param("status") InvoiceStatus status, @Param("date") LocalDate date,
                        Pageable pageable);

        @Query("SELECT i FROM Invoice i WHERE i.status <> :status AND i.dueDate < :date")
        Stream<Invoice> streamOverdue(@Param("status") InvoiceStatus status, @Param("date") LocalDate date);

        @Query(value = "SELECT * FROM invoices WHERE status <> :status AND due_date < :date", nativeQuery = true)
        Stream<Invoice> streamAllOverdueGlobal(@Param("status") InvoiceStatus status, @Param("date") LocalDate date);
}
