package com.ims.shared.audit;

import com.ims.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
  org.springframework.data.domain.Page<AuditLog> findByTenantId(
      Long tenantId, org.springframework.data.domain.Pageable pageable);
}
