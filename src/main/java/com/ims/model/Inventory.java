package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
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

  @NotNull
  @PositiveOrZero
  @Column(nullable = false)
  private Integer quantity;

  @PositiveOrZero
  @Column(name = "reserved_quantity")
  @Builder.Default
  private Integer reservedQuantity = 0;

  @Column(name = "low_stock_threshold")
  private Integer lowStockThreshold;

  @Column(name = "reorder_level")
  private Integer reorderLevel;

  public Integer getAvailableQuantity() {
    return quantity - (reservedQuantity != null ? reservedQuantity : 0);
  }

  public boolean isLowStock() {
    if (lowStockThreshold == null) {
      return false;
    }
    return quantity <= lowStockThreshold;
  }

  public boolean isBelowReorderLevel() {
    if (reorderLevel == null) {
      return false;
    }
    return quantity <= reorderLevel;
  }
}
