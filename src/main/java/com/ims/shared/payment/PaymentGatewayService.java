package com.ims.shared.payment;
import com.ims.model.Payment;
import com.ims.model.PaymentGatewayLog;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
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
        .tenantId(TenantContext.get())
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
        "payment_id", payment.getId()
    );
  }

  @Transactional
  public void processWebhook(Map<String, Object> payload) {
    String event = (String) payload.get("event");
    log.info("Processing payment gateway webhook: {}", event);

    // Simplified: in real scenario, validate signature here

    PaymentGatewayLog pgLog = PaymentGatewayLog.builder()
        .tenantId(0L) // Webhooks are usually global, need to extract tenant from payload if possible
        .eventType(event)
        .rawPayload(payload.toString())
        .build();
    logRepository.save(pgLog);

    if ("payment.captured".equals(event)) {
      // Map<String, Object> data = (Map<String, Object>) payload.get("payload");
      // Map<String, Object> paymentData = (Map<String, Object>) data.get("payment");
      
      // In real scenario, find payment by gatewayTransactionId
      // For this demo, let's assume we find it.
      // Payment payment = paymentRepository.findByGatewayTransactionId(gatewayOrderId);
      // update payment.status = 'COMPLETED'
      // update invoice.status = 'PAID'
    }
  }
}
