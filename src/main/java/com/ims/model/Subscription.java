package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "subscriptions", uniqueConstraints = {
    @jakarta.persistence.UniqueConstraint(columnNames = { "tenant_id" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @org.hibernate.annotations.TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private Long tenantId;

  @Column(nullable = false)
  private String plan;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private SubscriptionStatus status;

  @Column(name = "start_date", nullable = false)
  private OffsetDateTime startDate;

  @Column(name = "end_date", nullable = false)
  private OffsetDateTime endDate;

  @Column(name = "trial_end")
  private OffsetDateTime trialEnd;

  @Column(name = "created_at")
  @Builder.Default
  private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

  @Column(name = "updated_at")
  @Builder.Default
  private OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
}
