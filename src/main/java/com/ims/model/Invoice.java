package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "invoices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Invoice extends BaseEntity {

  @Column(name = "order_id")
  private Long orderId;

  @Column(name = "invoice_number", nullable = false)
  private String invoiceNumber;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(name = "tax_amount", precision = 12, scale = 2)
  private BigDecimal taxAmount;

  @Column(precision = 12, scale = 2)
  @Builder.Default
  private BigDecimal discount = BigDecimal.ZERO;

  @Column
  @Builder.Default
  private String status = "UNPAID";

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  @Column(name = "parent_invoice_id")
  private Long parentInvoiceId;
}
