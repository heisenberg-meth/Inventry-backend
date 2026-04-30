package com.ims.shared.webhook;

import com.ims.model.WebhookOutbox;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookOutboxRepository extends JpaRepository<WebhookOutbox, Long> {

    @Query("""
                SELECT w FROM WebhookOutbox w
                WHERE w.status IN (com.ims.model.WebhookOutbox.OutboxStatus.PENDING, com.ims.model.WebhookOutbox.OutboxStatus.FAILED)
                AND (w.nextRetryAt IS NULL OR w.nextRetryAt <= :now)
                ORDER BY w.createdAt ASC
            """)
    List<WebhookOutbox> findPendingForProcessing(@Param("now") LocalDateTime now, Pageable pageable);

    @Query(value = "SELECT * FROM webhook_outbox WHERE tenant_id = :tenantId", nativeQuery = true)
    List<WebhookOutbox> findByTenantIdUnfiltered(@Param("tenantId") Long tenantId);
}
