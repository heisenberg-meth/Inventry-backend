package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subscription_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String name;

  @Column(precision = 12, scale = 2)
  @Builder.Default
  private BigDecimal price = BigDecimal.ZERO;

  @Column
  @Builder.Default
  private String currency = "INR";

  @Column(name = "billing_cycle", nullable = false)
  private String billingCycle;

  @Column(name = "duration_days")
  @Builder.Default
  private Integer durationDays = 30;

  @Column(columnDefinition = "TEXT")
  private String features;

  @Column(name = "max_users")
  @Builder.Default
  private Integer maxUsers = 0;

  @Column(name = "max_products")
  @Builder.Default
  private Integer maxProducts = 0;

  @Column
  @Builder.Default
  private String status = "ACTIVE";

  @Column
  @Builder.Default
  private Integer version = 1;

  @Column(name = "updated_by")
  private Long updatedBy;

  @Column(name = "created_at")
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at")
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();
}
