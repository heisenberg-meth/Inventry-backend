package com.ims.tenant.repository;

import com.ims.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query("SELECT c FROM Customer c WHERE c.tenantId = :tenantId")
    Page<Customer> findByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.tenantId = :tenantId")
    List<Customer> findAllByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT c FROM Customer c WHERE c.id = :id AND c.tenantId = :tenantId")
    java.util.Optional<Customer> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
