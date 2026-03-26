package com.ims.tenant.service;

import com.ims.dto.CreatePaymentRequest;
import com.ims.model.Invoice;
import com.ims.model.Payment;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final InvoiceRepository invoiceRepository;

  @Transactional
  public Payment processPayment(CreatePaymentRequest request) {
    Long userId = null;
    try {
      userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    } catch (Exception e) {
      log.trace("Caught expected exception for anonymous payment process: {}", e.getMessage());
    }

    Invoice invoice =
        invoiceRepository
            .findById(request.getInvoiceId())
            .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

    Payment payment =
        Payment.builder()
            .invoiceId(invoice.getId())
            .amount(request.getAmount())
            .paymentMode(request.getPaymentMode())
            .reference(request.getReference())
            .notes(request.getNotes())
            .createdBy(userId)
            .build();

    payment = paymentRepository.save(payment);

    BigDecimal totalPaid = paymentRepository.sumAmountByInvoiceId(invoice.getId());
    BigDecimal totalDue = invoice.getAmount();
    if (invoice.getTaxAmount() != null) {
      totalDue = totalDue.add(invoice.getTaxAmount());
    }

    if (totalPaid.compareTo(totalDue) >= 0) {
      invoice.setStatus("PAID");
      invoice.setPaidAt(LocalDateTime.now());
    } else {
      invoice.setStatus("PARTIAL");
    }

    invoiceRepository.save(invoice);

    return payment;
  }

  public Page<Payment> getPayments(Pageable pageable) {
    return paymentRepository.findAll(pageable);
  }

  public Payment getPaymentById(Long id) {
    return paymentRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Payment not found"));
  }
}
