package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(name = "workspace_slug", unique = true)
  private String workspaceSlug;

  @Column(name = "business_type", nullable = false)
  private String businessType;

  @Column @Builder.Default private String plan = "FREE";

  @Column @Builder.Default private String status = "ACTIVE";

  @Column(name = "invoice_sequence")
  @Builder.Default
  private Integer invoiceSequence = 0;

  @Column(name = "max_products")
  private Integer maxProducts;

  @Column(name = "max_users")
  private Integer maxUsers;

  @Column(name = "expiry_threshold_days")
  @Builder.Default
  private Integer expiryThresholdDays = 30;

  @Column
  private String address;

  @Column
  private String gstin;

  @Column(name = "created_at")
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
