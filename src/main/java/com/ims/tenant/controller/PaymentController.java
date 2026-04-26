package com.ims.tenant.controller;

import com.ims.dto.PaymentRequest;
import com.ims.model.Payment;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
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
@RequestMapping("/api/v1/tenant/payments")
@RequiredArgsConstructor
@Tag(name = "Tenant - Payments", description = "Payment tracking")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

  private final PaymentService paymentService;

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Record payment against invoice")
  public ResponseEntity<Payment> recordPayment(@Valid @RequestBody PaymentRequest request) {
    Payment payment =
        paymentService.recordPayment(
            Objects.requireNonNull(request.getInvoiceId()),
            Objects.requireNonNull(request.getAmount()),
            Objects.requireNonNull(request.getPaymentMode()),
            Objects.requireNonNull(request.getReference()),
            Objects.requireNonNull(request.getNotes()),
            Objects.requireNonNull(request.getUserId()));
    return ResponseEntity.status(HttpStatus.CREATED).body(payment);
  }

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "List mappings")
  public ResponseEntity<Page<Payment>> list(Pageable pageable) {
    return ResponseEntity.ok(paymentService.getPayments(pageable));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Get payment details")
  public ResponseEntity<Payment> get(@PathVariable Long id) {
    return ResponseEntity.ok(paymentService.getById(id));
  }
}
