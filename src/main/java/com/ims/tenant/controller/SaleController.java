package com.ims.tenant.controller;

import com.ims.model.Order;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.dto.OrderRequest;
import com.ims.tenant.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/sales")
@RequiredArgsConstructor
@Tag(name = "Tenant - Sales", description = "Sales and Billing management")
@SecurityRequirement(name = "bearerAuth")
public class SaleController {

  private final OrderService orderService;

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(
      summary = "Record a sale with billing",
      description = "Creates a sales order and automatically generates an invoice")
  public ResponseEntity<Order> createSale(@Valid @RequestBody OrderRequest request) {
    Long userId =
        (Long)
            Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication())
                .getPrincipal();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orderService.createSalesOrder(request, Objects.requireNonNull(userId)));
  }
}
