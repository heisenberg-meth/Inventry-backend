package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;
import org.springframework.lang.Nullable;

@Entity
@Table(name = "invoices", uniqueConstraints = {
    @UniqueConstraint(name = "unique_invoice_per_tenant", columnNames = { "tenant_id", "invoice_number" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Nullable
  private Long id;

  @TenantId
  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "order_id")
  @Nullable
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
  @Enumerated(jakarta.persistence.EnumType.STRING)
  @Builder.Default
  private InvoiceStatus status = InvoiceStatus.UNPAID;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(name = "paid_at")
  @Nullable
  private LocalDateTime paidAt;

  @Column(name = "parent_invoice_id")
  @Nullable
  private Long parentInvoiceId;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private boolean active = true;

  @Column(name = "created_at")
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
