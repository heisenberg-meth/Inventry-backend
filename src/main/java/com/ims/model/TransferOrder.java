package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "transfer_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TransferOrder extends BaseEntity {

  @Column(name = "from_location", nullable = false)
  private String fromLocation;

  @Column(name = "to_location", nullable = false)
  private String toLocation;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "quantity", nullable = false)
  private Integer quantity;

  @Column
  @Builder.Default
  private String status = "PENDING";

  @Column
  private String notes;

  @Column(name = "created_by")
  private Long createdBy;
}
