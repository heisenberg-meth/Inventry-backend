package com.ims.tenant.controller;

import com.ims.dto.CreatePaymentRequest;
import com.ims.model.Payment;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/payments")
@RequiredArgsConstructor
@Tag(name = "Tenant - Payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

  private final PaymentService paymentService;

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Record payment against invoice")
  public ResponseEntity<Payment> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.processPayment(request));
  }

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "List all payments")
  public ResponseEntity<Page<Payment>> getPayments(Pageable pageable) {
    return ResponseEntity.ok(paymentService.getPayments(pageable));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Get payment detail")
  public ResponseEntity<Payment> getPaymentById(@PathVariable Long id) {
    return ResponseEntity.ok(paymentService.getPaymentById(id));
  }
}
