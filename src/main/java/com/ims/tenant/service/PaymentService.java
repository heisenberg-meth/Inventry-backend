package com.ims.tenant.service;

import com.ims.model.Invoice;
import com.ims.model.InvoiceStatus;
import com.ims.model.Payment;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.ResourceNotFoundException;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final InvoiceRepository invoiceRepository;

  @Transactional
  public Payment recordPayment(
      Long invoiceId,
      BigDecimal amount,
      String mode,
      String reference,
      String notes,
      Long userId) {
    Invoice invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

    if (InvoiceStatus.PAID == invoice.getStatus()) {
      throw new IllegalArgumentException("Invoice is already fully PAID");
    }

    Payment payment =
        Payment.builder()
            .tenantId(TenantContext.requireTenantId())
            .invoiceId(invoiceId)
            .amount(amount)
            .paymentMode(mode)
            .reference(reference)
            .notes(notes)
            .createdBy(userId)
            .createdAt(Objects.requireNonNull(LocalDateTime.now()))
            .build();

    payment = paymentRepository.save(Objects.requireNonNull(payment));

    // Update invoice status
    BigDecimal totalPaid = paymentRepository.sumAmountByInvoiceId(invoiceId);
    BigDecimal invoiceAmount = invoice.getAmount();
    if (invoice.getTaxAmount() != null) {
      invoiceAmount = invoiceAmount.add(invoice.getTaxAmount());
    }
    if (invoice.getDiscount() != null) {
      invoiceAmount = invoiceAmount.subtract(invoice.getDiscount());
    }

    if (totalPaid.compareTo(invoiceAmount) >= 0) {
      invoice.setStatus(InvoiceStatus.PAID);
      invoice.setPaidAt(Objects.requireNonNull(LocalDateTime.now()));
    } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
      invoice.setStatus(InvoiceStatus.PARTIAL);
    }

    invoiceRepository.save(Objects.requireNonNull(invoice));

    return Objects.requireNonNull(payment);
  }

  public Page<Payment> getPayments(Pageable pageable) {
    TenantContext.assertTenantPresent();
    return paymentRepository.findAll(pageable);
  }

  public Payment getById(Long id) {
    TenantContext.assertTenantPresent();
    return Objects.requireNonNull(
        paymentRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id)));
  }
}
