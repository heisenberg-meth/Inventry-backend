package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
  @SequenceGenerator(name = "order_seq", sequenceName = "orders_id_seq", allocationSize = 50)
  private Long id;

  @TenantId
  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(nullable = false)
  private String type;

  @Column(nullable = false)
  @Builder.Default
  private String currency = "INR";

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
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

  @Column private String notes;

  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "reference_order_id")
  private Long referenceOrderId;

  @Column(name = "created_at")
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
