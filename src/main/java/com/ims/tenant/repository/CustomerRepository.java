package com.ims.tenant.repository;

import com.ims.model.Customer;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

  @Query("SELECT c FROM Customer c WHERE c.tenantId = :tenantId AND c.isDeleted = false")
  Page<Customer> findAllByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

  @Query(
      "SELECT c FROM Customer c WHERE c.id = :id AND c.tenantId = :tenantId AND c.isDeleted = false")
  Optional<Customer> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

  boolean existsByTenantIdAndEmailAndIsDeletedFalse(Long tenantId, String email);
}
