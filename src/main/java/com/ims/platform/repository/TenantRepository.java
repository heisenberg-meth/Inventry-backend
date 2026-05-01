package com.ims.platform.repository;

import com.ims.model.Tenant;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
  Optional<Tenant> findByWorkspaceSlug(String workspaceSlug);

  boolean existsByWorkspaceSlug(String workspaceSlug);

  Optional<Tenant> findByCompanyCode(String companyCode);

  boolean existsByCompanyCode(String companyCode);

  Optional<Tenant> findByIdempotencyKey(String idempotencyKey);

  List<Tenant> findAllByCreatedAtBefore(LocalDateTime dateTime);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM Tenant t WHERE t.id = :id")
  Optional<Tenant> lockById(@Param("id") Long id);

  @Query("SELECT t.id FROM Tenant t")
  List<Long> findAllIds();
}
