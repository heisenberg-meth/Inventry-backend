package com.ims.shared.audit;

import com.ims.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE a.tenantId = :tenantId ORDER BY a.createdAt DESC")
    Page<AuditLog> findByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.id = :id AND a.tenantId = :tenantId")
    java.util.Optional<AuditLog> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Query(value = "SELECT * FROM audit_logs", nativeQuery = true)
    Page<AuditLog> findAllGlobal(Pageable pageable);
}
