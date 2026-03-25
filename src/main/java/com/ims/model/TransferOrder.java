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
@Table(name = "transfer_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferOrder {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "from_location", nullable = false)
  private String fromLocation;

  @Column(name = "to_location", nullable = false)
  private String toLocation;

  @Column @Builder.Default private String status = "PENDING";

  @Column private String notes;

  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "created_at")
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
