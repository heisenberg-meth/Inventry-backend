package com.ims.platform.repository;

import com.ims.model.Tenant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
  Optional<Tenant> findByWorkspaceSlug(String workspaceSlug);

  boolean existsByWorkspaceSlug(String workspaceSlug);

  Optional<Tenant> findByCompanyCode(String companyCode);

  boolean existsByCompanyCode(String companyCode);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("SELECT t FROM Tenant t WHERE t.id = :id")
  Optional<Tenant> findByIdWithLock(@org.springframework.data.repository.query.Param("id") Long id);
}
