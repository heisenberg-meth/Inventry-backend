package com.ims.tenant.controller;

import com.ims.shared.payment.PaymentGatewayService;
import com.ims.shared.rbac.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tenant/payments/gateway")
@RequiredArgsConstructor
@Tag(name = "Tenant - Payments Gateway", description = "Online payment integration")
public class PaymentGatewayController {

  private final PaymentGatewayService gatewayService;

  @PostMapping("/initiate")
  @RequiresRole({"ADMIN", "MANAGER"})
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Initiate a gateway payment")
  public ResponseEntity<Map<String, Object>> initiate(@RequestBody Map<String, Object> body) {
    Long invoiceId = Long.valueOf(body.get("invoice_id").toString());
    BigDecimal amount = new BigDecimal(body.get("amount").toString());
    Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    
    return ResponseEntity.ok(gatewayService.initiatePayment(invoiceId, amount, userId));
  }

  @PostMapping("/webhook")
  @Operation(summary = "Gateway webhook handler")
  public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, Object> payload) {
    gatewayService.processWebhook(payload);
    return ResponseEntity.ok().build();
  }
}
