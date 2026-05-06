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

public class PaymentGatewayService {

  private final PaymentRepository paymentRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentGatewayLogRepository logRepository;
  private final com.ims.tenant.service.PaymentService paymentService;

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
        "payment_id", payment.getId());
  }

  @Transactional
  public void processWebhook(Map<String, Object> payload) {
    String event = (String) payload.get("event");
    log.info("Processing payment gateway webhook: {}", event);

    // Simplified: in real scenario, validate signature here
    // Extract tenantId from payload if possible, otherwise use 0 for global log
    Long tenantId = payload.containsKey("tenant_id") ? Long.valueOf(payload.get("tenant_id").toString()) : 0L;

    try {
      if (tenantId != 0L) {
        TenantContext.setTenantId(tenantId);
      }

      PaymentGatewayLog pgLog = PaymentGatewayLog.builder()
          .tenantId(tenantId)
          .eventType(event)
          .rawPayload(payload.toString())
          .build();
      logRepository.save(pgLog);

      if ("payment.captured".equals(event)) {
        Object payloadObj = payload.get("payload");
        if (payloadObj instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> data = (Map<String, Object>) payloadObj;
          Object paymentObj = data.get("payment");
          if (paymentObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paymentData = (Map<String, Object>) paymentObj;
            String gatewayOrderId = (String) paymentData.get("order_id");
            String reference = (String) paymentData.get("id");
            if (gatewayOrderId != null) {
              paymentService.updatePaymentStatus(gatewayOrderId, "COMPLETED", reference);
            }
          }
        }
      } else if ("payment.failed".equals(event)) {
        Object payloadObj = payload.get("payload");
        if (payloadObj instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> data = (Map<String, Object>) payloadObj;
          Object paymentObj = data.get("payment");
          if (paymentObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paymentData = (Map<String, Object>) paymentObj;
            String gatewayOrderId = (String) paymentData.get("order_id");
            if (gatewayOrderId != null) {
              paymentService.updatePaymentStatus(gatewayOrderId, "FAILED", null);
            }
          }
        }
      }
    } finally {
      TenantContext.clear();
    }
  }
}
