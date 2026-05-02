package com.ims.shared.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.ims.model.Payment;
import com.ims.model.PaymentStatus;
import com.ims.model.InvoiceStatus;
import com.ims.model.PaymentGatewayLog;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayService {

  /** Length of the short hex fragment appended to generated gateway order IDs. */
  private static final int ORDER_ID_SUFFIX_LENGTH = 8;

  /**
   * Razorpay reports payment amounts in paise; we divide by 100 to get the rupee
   * value.
   */
  private static final BigDecimal PAISE_PER_RUPEE = new BigDecimal(100);

  private final PaymentRepository paymentRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentGatewayLogRepository logRepository;

  @Transactional
  public Map<String, Object> initiatePayment(
      Long invoiceId, BigDecimal amount, Long userId) {
    Long tenantId = TenantContext.requireTenantId();
    invoiceRepository
        .findById(invoiceId)
        .filter(inv -> tenantId.equals(inv.getTenantId()))
        .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

    String gatewayOrderId = "order_" + UUID.randomUUID().toString().substring(0, ORDER_ID_SUFFIX_LENGTH);

    Payment payment = Objects.requireNonNull(
        Payment.builder()
            .invoiceId(invoiceId)
            .amount(amount)
            .paymentMode("GATEWAY")
            .gatewayTransactionId(gatewayOrderId)
            .status(PaymentStatus.PENDING)
            .createdBy(userId)
            .build());

    paymentRepository.save(payment);

    return Map.of(
        "gateway_order_id",
        gatewayOrderId,
        "amount",
        amount,
        "currency",
        "INR",
        "payment_id",
        payment.getId(),
        "tenant_id",
        payment.getTenantId()); // Client should pass this to Razorpay in 'notes'
  }

  @Transactional(readOnly = true)
  public boolean isEventProcessed(String eventId) {
    return logRepository.existsByEventId(eventId);
  }

  @Transactional
  public void processWebhook(Long tenantId, String eventId, String eventType, JsonNode payload) {
    log.info("Processing validated payment gateway webhook: {} for tenant {}", eventType, tenantId);

    Long previousTenantId = TenantContext.getTenantId();
    TenantContext.setTenantId(tenantId);
    try {
      PaymentGatewayLog pgLog = Objects.requireNonNull(
          PaymentGatewayLog.builder()
              .eventId(Objects.requireNonNull(eventId))
              .eventType(Objects.requireNonNull(eventType))
              .rawPayload(Objects.requireNonNull(payload.toString()))
              .build());
      logRepository.save(pgLog);

      if ("payment.captured".equals(eventType)) {
        String gatewayOrderId = payload.path("payload").path("payment").path("entity").path("order_id").asText();
        BigDecimal amount = new BigDecimal(
            payload.path("payload").path("payment").path("entity").path("amount").asLong())
            .divide(PAISE_PER_RUPEE); // Razorpay reports amount in paise
        String currency = payload.path("payload").path("payment").path("entity").path("currency").asText();
        String gatewayPaymentId = payload.path("payload").path("payment").path("entity").path("id").asText();

        // 1. Locate and validate payment record
        Payment payment = paymentRepository
            .findByGatewayTransactionId(gatewayOrderId)
            .orElseThrow(
                () -> new EntityNotFoundException("Payment not found for order: " + gatewayOrderId));

        if (PaymentStatus.PAID.equals(payment.getStatus())) {
          log.info("Payment {} already processed, skipping state update", gatewayOrderId);
          return;
        }

        // 2. Cross-verify amount and currency
        if (payment.getAmount().compareTo(amount) != 0 || !"INR".equals(currency)) {
          log.error(
              "CRITICAL: Payment mismatch for order {}. Expected: {} INR, Received: {} {}",
              gatewayOrderId,
              payment.getAmount(),
              amount,
              currency);
          payment.setStatus(PaymentStatus.FAILED);
          payment.setNotes("Amount/Currency mismatch on webhook");
          paymentRepository.save(payment);
          return;
        }

        // 3. Update Payment state
        payment.setStatus(PaymentStatus.PAID);
        payment.setReference(Objects.requireNonNull(gatewayPaymentId));
        paymentRepository.save(payment);

        // 4. Update Invoice state
        invoiceRepository
            .findById(Objects.requireNonNull(payment.getInvoiceId()))
            .filter(inv -> tenantId.equals(inv.getTenantId()))
            .ifPresent(
                invoice -> {
                  invoice.setStatus(InvoiceStatus.PAID);
                  invoice.setPaidAt(Objects.requireNonNull(LocalDateTime.now()));
                  invoiceRepository.save(invoice);
                  log.info("Invoice {} marked as PAID", invoice.getId());
                });

        log.info("Successfully processed payment capture for order {}", gatewayOrderId);
      }
    } finally {
      if (previousTenantId != null) {
        TenantContext.setTenantId(previousTenantId);
      } else {
        TenantContext.clear();
      }
    }
  }
}
