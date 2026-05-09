package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Payment extends BaseEntity {

  @Column(name = "invoice_id", nullable = false)
  private Long invoiceId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(name = "payment_mode")
  private String paymentMode;

  @Column(name = "gateway_transaction_id")
  private String gatewayTransactionId;

  @Column @Builder.Default private String status = "PENDING";

  @Column private String reference;

  @Column private String notes;

  @Column(name = "created_by")
  private Long createdBy;
}
