package com.ims.tenant.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.shared.exception.UnauthorizedException;
import com.ims.shared.payment.PaymentGatewayService;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.TenantSecretService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/payments/gateway")
@RequiredArgsConstructor
@Tag(name = "Tenant - Payments Gateway", description = "Online payment integration")
public class PaymentGatewayController {

  private final PaymentGatewayService gatewayService;
  private final TenantSecretService secretService;
  private final ObjectMapper objectMapper;

  @PostMapping("/initiate")
  @RequiresRole({"ADMIN", "MANAGER"})
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Initiate a gateway payment")
  public ResponseEntity<Map<String, Object>> initiate(@RequestBody Map<String, Object> body) {
    Long invoiceId = Long.valueOf(body.get("invoice_id").toString());
    BigDecimal amount = new BigDecimal(body.get("amount").toString());
    Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    return ResponseEntity.ok(
        gatewayService.initiatePayment(
            Objects.requireNonNull(invoiceId),
            Objects.requireNonNull(amount),
            Objects.requireNonNull(userId)));
  }

  @PostMapping("/webhook")
  @Operation(summary = "Gateway webhook handler")
  public ResponseEntity<Void> handleWebhook(HttpServletRequest request) {
    try {
      // 1. Get raw body (crucial for signature verification)
      String rawBody =
          request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));

      // 2. Extract signature header
      String receivedSig = request.getHeader("X-Razorpay-Signature");
      if (receivedSig == null) {
        throw new UnauthorizedException("Missing webhook signature");
      }

      // 3. Parse payload to identify tenant and event
      JsonNode root = objectMapper.readTree(rawBody);

      // Extract tenant ID from notes
      Long tenantId = extractTenantId(root);
      if (tenantId == null) {
        throw new UnauthorizedException("Unable to identify tenant from payload");
      }

      // 4. Resolve tenant-aware secret
      String webhookSecret = secretService.getWebhookSecret(tenantId);
      if (webhookSecret == null) {
        throw new UnauthorizedException("Webhook secret not configured for tenant");
      }

      // 5. Verify signature
      String expectedSig = hmacSha256(rawBody, webhookSecret);
      if (!MessageDigest.isEqual(
          expectedSig.getBytes(StandardCharsets.UTF_8),
          receivedSig.getBytes(StandardCharsets.UTF_8))) {
        throw new UnauthorizedException("Invalid webhook signature");
      }

      final String eventType = root.path("event").asText();
      final String eventId = root.path("id").asText(); // Razorpay event ID

      // 6. Enforce idempotency
      if (gatewayService.isEventProcessed(eventId)) {
        return ResponseEntity.ok().build();
      }

      // 7. Strict event type validation
      if (!"payment.captured".equals(eventType)) {
        return ResponseEntity.ok().build(); // Ignore other events but return 200
      }

      // 8. Process business logic
      gatewayService.processWebhook(tenantId, eventId, eventType, root);

      return ResponseEntity.ok().build();
    } catch (Exception e) {
      if (e instanceof UnauthorizedException) {
        throw (UnauthorizedException) e;
      }
      throw new IllegalStateException("Webhook processing failed", e);
    }
  }

  private Long extractTenantId(JsonNode root) {
    // Try to find tenant_id in notes (common practice)
    JsonNode notes = root.path("payload").path("payment").path("entity").path("notes");
    if (notes.has("tenant_id")) {
      return notes.get("tenant_id").asLong();
    }
    // Fallback or other extraction logic can go here
    return null;
  }

  private String hmacSha256(String data, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      SecretKeySpec keySpec =
          new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
      mac.init(keySpec);
      byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(rawHmac);
    } catch (Exception e) {
      throw new IllegalStateException("HMAC calculation failed", e);
    }
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder hex = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }
}
