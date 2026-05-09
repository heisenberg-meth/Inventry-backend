package com.ims.product;

import com.ims.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@lombok.experimental.SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Product extends BaseEntity {

  private static final int DEFAULT_REORDER_LEVEL = 10;

  @Column(nullable = false)
  private String name;

  @Column private String sku;

  @Column private String description;

  @Column private String barcode;

  @Column(name = "category_id")
  private Long categoryId;

  @Column private String unit;

  @jakarta.validation.constraints.PositiveOrZero
  @Column(name = "purchase_price", precision = 10, scale = 2)
  private BigDecimal purchasePrice;

  @jakarta.validation.constraints.PositiveOrZero
  @Column(name = "sale_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal salePrice;

  @jakarta.validation.constraints.PositiveOrZero @Column @Builder.Default private Integer stock = 0;

  @Column(name = "reorder_level")
  @jakarta.validation.constraints.PositiveOrZero
  @Builder.Default
  private Integer reorderLevel = DEFAULT_REORDER_LEVEL;

  @Column(name = "is_deleted")
  @Builder.Default
  private Boolean isDeleted = false;
}
