package com.ims.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

  private static final int DEFAULT_REORDER_LEVEL = 10;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  private Long version;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private boolean active = true;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private Long tenantId;

  @Column(nullable = false)
  private String name;

  @Column
  private String sku;

  @Column
  private String barcode;

  @Column(name = "category_id")
  private Long categoryId;

  @Column
  private String unit;

  @Column(name = "purchase_price", precision = 10, scale = 2)
  private BigDecimal purchasePrice;

  @Column(name = "sale_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal salePrice;

  @Column
  @Builder.Default
  private Integer stock = 0;

  @Column(name = "reorder_level")
  @Builder.Default
  private Integer reorderLevel = DEFAULT_REORDER_LEVEL;

  @Column(name = "created_at")
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at")
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();
}
