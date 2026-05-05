package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "subscription_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SubscriptionPlan extends BaseEntity {

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

  @Column(name = "updated_by")
  private Long updatedBy;
}
