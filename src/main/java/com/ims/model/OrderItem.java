package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_item_seq")
  @SequenceGenerator(
      name = "order_item_seq",
      sequenceName = "order_items_id_seq",
      allocationSize = 50)
  private Long id;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal unitPrice;

  @Column(precision = 10, scale = 2)
  @Builder.Default
  private BigDecimal discount = BigDecimal.ZERO;

  @Column(name = "tax_rate", precision = 5, scale = 2)
  @Builder.Default
  private BigDecimal taxRate = BigDecimal.ZERO;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal total;

  @Column(name = "returned_quantity")
  @Builder.Default
  private Integer returnedQuantity = 0;
}
