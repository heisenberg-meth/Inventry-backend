package com.ims.shared.payment;

import com.ims.model.Payment;
import com.ims.model.PaymentGatewayLog;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayService {

  private final PaymentRepository paymentRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentGatewayLogRepository logRepository;

  @Transactional
  public Map<String, Object> initiatePayment(Long invoiceId, BigDecimal amount, Long userId) {
    invoiceRepository.findById(invoiceId)
        .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

    String gatewayOrderId = "order_" + UUID.randomUUID().toString().substring(0, 8);

    Payment payment = Payment.builder()
        .tenantId(TenantContext.getTenantId())
        .invoiceId(invoiceId)
        .amount(amount)
        .paymentMode("GATEWAY")
        .gatewayTransactionId(gatewayOrderId)
        .status("PENDING")
        .createdBy(userId)
        .build();

    paymentRepository.save(payment);

    return Map.of(
        "gateway_order_id", gatewayOrderId,
        "amount", amount,
        "currency", "INR",
        "payment_id", payment.getId(),
        "tenant_id", payment.getTenantId()); // Client should pass this to Razorpay in 'notes'
  }

  @Transactional(readOnly = true)
  public boolean isEventProcessed(String eventId) {
    return logRepository.existsByEventId(eventId);
  }

  @Transactional
  public void processWebhook(Long tenantId, String eventId, String eventType, JsonNode payload) {
    log.info("Processing validated payment gateway webhook: {} for tenant {}", eventType, tenantId);

    PaymentGatewayLog pgLog = PaymentGatewayLog.builder()
        .tenantId(tenantId)
        .eventId(eventId)
        .eventType(eventType)
        .rawPayload(payload.toString())
        .build();
    logRepository.save(pgLog);

    if ("payment.captured".equals(eventType)) {
      String gatewayOrderId = payload.path("payload").path("payment").path("entity").path("order_id").asText();
      BigDecimal amount = new BigDecimal(payload.path("payload").path("payment").path("entity").path("amount").asLong())
          .divide(new BigDecimal(100)); // Razorpay amount is in paise
      String currency = payload.path("payload").path("payment").path("entity").path("currency").asText();

      // Validate business details
      // Payment payment = paymentRepository.findByGatewayTransactionId(gatewayOrderId)
      //    .orElseThrow(() -> new EntityNotFoundException("Payment not found"));
      
      // if (!payment.getAmount().equals(amount)) { ... }
      // if (!"INR".equals(currency)) { ... }

      log.info("Payment captured for order {}: {} {}", gatewayOrderId, amount, currency);
      
      // Update payment and invoice status here
    }
  }
}
