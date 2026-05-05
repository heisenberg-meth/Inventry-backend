package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "stock_movements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class StockMovement extends BaseEntity {

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "movement_type", nullable = false)
  private String movementType;

  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "previous_stock")
  private Integer previousStock;

  @Column(name = "new_stock")
  private Integer newStock;

  @Column(name = "reference_id")
  private Long referenceId;

  @Column(name = "reference_type")
  private String referenceType;

  @Column
  private String notes;

  @Column(name = "created_by")
  private Long createdBy;
}
