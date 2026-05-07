package com.ims.tenant.service;

import com.ims.model.Invoice;
import com.ims.model.Payment;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.ResourceNotFoundException;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
@Slf4j
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final InvoiceRepository invoiceRepository;
  private final com.ims.shared.outbox.OutboxService outboxService;
  private final com.ims.shared.metrics.BusinessMetricsService businessMetricsService;

  @Transactional
  public Payment recordPayment(Long invoiceId, BigDecimal amount, String mode, String reference, String notes,
      Long userId) {
    Long tenantId = TenantContext.getTenantId();
    Invoice invoice = invoiceRepository.findByIdAndTenantId(tenantId, invoiceId)
        .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

    if ("PAID".equals(invoice.getStatus())) {
      throw new IllegalArgumentException("Invoice is already fully PAID");
    }

    Payment payment = Payment.builder()
        .tenantId(tenantId)
        .invoiceId(invoiceId)
        .amount(amount)
        .paymentMode(mode)
        .status("COMPLETED")
        .reference(reference)
        .notes(notes)
        .createdBy(userId)
        .build();

    payment = paymentRepository.save(Objects.requireNonNull(payment));

    BigDecimal totalPaid = paymentRepository.sumAmountByTenantIdAndInvoiceId(tenantId, invoiceId);
    BigDecimal invoiceAmount = invoice.getAmount();
    if (invoice.getTaxAmount() != null) {
      invoiceAmount = invoiceAmount.add(invoice.getTaxAmount());
    }
    if (invoice.getDiscount() != null) {
      invoiceAmount = invoiceAmount.subtract(invoice.getDiscount());
    }

    if (totalPaid.compareTo(invoiceAmount) >= 0) {
      invoice.setStatus("PAID");
      invoice.setPaidAt(LocalDateTime.now());
    } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
      invoice.setStatus("PARTIAL");
    }

    invoiceRepository.save(Objects.requireNonNull(invoice));
    log.info("Payment recorded: {} for invoice {}. New status: {}", amount, invoiceId, invoice.getStatus());

    // Save to Outbox for downstream processing
    outboxService.saveEvent("PAYMENT", payment.getId().toString(), "RECORDED", java.util.Map.of(
        "paymentId", payment.getId(),
        "invoiceId", invoiceId,
        "amount", amount,
        "status", payment.getStatus()
    ));

    return Objects.requireNonNull(payment);
  }

  @Transactional
  public void updatePaymentStatus(String gatewayTransactionId, String status, String reference) {
    Long tenantId = TenantContext.getTenantId();
    Payment payment = paymentRepository.findByTenantIdAndGatewayTransactionId(tenantId, gatewayTransactionId)
        .orElseThrow(() -> new ResourceNotFoundException("Payment not found for gateway transaction: " + gatewayTransactionId));

    if ("COMPLETED".equals(payment.getStatus())) {
      log.warn("Payment {} already COMPLETED, skipping", gatewayTransactionId);
      return;
    }

    payment.setStatus(status);
    payment.setReference(reference);
    paymentRepository.save(payment);

    if ("FAILED".equals(status)) {
      businessMetricsService.incrementFailedPayments();
    }

    if ("COMPLETED".equals(status)) {
      updateInvoiceStatus(payment.getInvoiceId());
    }
  }

  private void updateInvoiceStatus(Long invoiceId) {
    Long tenantId = TenantContext.getTenantId();
    Invoice invoice = invoiceRepository.findByIdAndTenantId(tenantId, invoiceId)
        .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

    BigDecimal totalPaid = paymentRepository.sumAmountByTenantIdAndInvoiceId(tenantId, invoiceId);
    BigDecimal invoiceAmount = invoice.getAmount();
    if (invoice.getTaxAmount() != null) {
      invoiceAmount = invoiceAmount.add(invoice.getTaxAmount());
    }
    if (invoice.getDiscount() != null) {
      invoiceAmount = invoiceAmount.subtract(invoice.getDiscount());
    }

    if (totalPaid.compareTo(invoiceAmount) >= 0) {
      invoice.setStatus("PAID");
      invoice.setPaidAt(LocalDateTime.now());
    } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
      invoice.setStatus("PARTIAL");
    }

    invoiceRepository.save(invoice);
    log.info("Invoice {} status updated to {} based on total payments {}", invoiceId, invoice.getStatus(), totalPaid);
  }

  public Page<Payment> getPayments(Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    return paymentRepository.findAllByTenantId(tenantId, pageable);
  }

  public Payment getById(Long id) {
    Long tenantId = TenantContext.getTenantId();
    return paymentRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
  }
}
