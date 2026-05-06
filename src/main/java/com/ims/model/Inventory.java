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
@Table(name = "inventory")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Inventory extends BaseEntity {

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "reserved_quantity")
  private Integer reservedQuantity;

  @Column(name = "low_stock_threshold")
  private Integer lowStockThreshold;

  @Column(name = "reorder_level")
  private Integer reorderLevel;
}
