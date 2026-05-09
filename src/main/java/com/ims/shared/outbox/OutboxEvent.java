package com.ims.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "aggregate_type", nullable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private String aggregateId;

  @Column(nullable = false)
  private String type;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  private String payload;

  @Column(length = 20)
  @Builder.Default
  private String status = "PENDING";

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "retry_count")
  @Builder.Default
  private Integer retryCount = 0;

  @Column(name = "created_at")
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "processed_at")
  private LocalDateTime processedAt;
}
