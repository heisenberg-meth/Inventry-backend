package com.ims.model;

import org.hibernate.annotations.TenantId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @TenantId
  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "invoice_id", nullable = false)
  private Long invoiceId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false)
  @Builder.Default
  private String currency = "INR";

  @Column(name = "payment_mode")
  private String paymentMode;

  @Column(name = "gateway_transaction_id")
  private String gatewayTransactionId;

  @Column
  @Builder.Default
  private String status = "PENDING";

  @Column private String reference;

  @Column private String notes;

  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "created_at")
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
