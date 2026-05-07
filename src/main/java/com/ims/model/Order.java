package com.ims.model;

import com.ims.order.entity.OrderStatus;
import com.ims.order.entity.OrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Order extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private OrderStatus status = OrderStatus.PENDING;

  @Column(name = "customer_id")
  private Long customerId;

  @Column(name = "supplier_id")
  private Long supplierId;

  @Column(name = "total_amount", precision = 12, scale = 2)
  private BigDecimal totalAmount;

  @Column(name = "tax_amount", precision = 12, scale = 2)
  private BigDecimal taxAmount;

  @Column(precision = 12, scale = 2)
  @Builder.Default
  private BigDecimal discount = BigDecimal.ZERO;

  @Column
  private String notes;

  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "reference_order_id")
  private Long referenceOrderId;
}
