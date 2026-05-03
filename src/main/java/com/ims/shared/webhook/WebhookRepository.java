package com.ims.shared.webhook;

import com.ims.model.Webhook;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, Long> {
    @Query("SELECT w FROM Webhook w WHERE w.tenantId = :tenantId")
    List<Webhook> findAllByTenantId(@Param("tenantId") Long tenantId);
}
