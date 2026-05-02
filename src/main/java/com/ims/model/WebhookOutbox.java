package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "webhook_outbox")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookOutbox {

  public enum OutboxStatus {
    PENDING, PROCESSING, COMPLETED, FAILED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private Long tenantId;

  @Column(name = "webhook_id")
  private Long webhook_id;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private OutboxStatus status = OutboxStatus.PENDING;

  @Column(name = "retry_count")
  @Builder.Default
  private Integer retryCount = 0;

  @Column(name = "next_retry_at")
  private LocalDateTime nextRetryAt;

  @Column(name = "last_error", columnDefinition = "TEXT")
  private String lastError;

  @Column(name = "created_at", nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "processed_at")
  private LocalDateTime processedAt;
}
