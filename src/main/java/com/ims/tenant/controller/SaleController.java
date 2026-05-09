package com.ims.tenant.controller;

import com.ims.order.dto.CreateOrderRequest;
import com.ims.order.dto.OrderResponse;
import com.ims.shared.auth.JwtAuthenticationToken;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/sales")
@RequiredArgsConstructor
@Tag(name = "Tenant - Sales", description = "Sales and Billing management")
@SecurityRequirement(name = "bearerAuth")
public class SaleController {

  private final OrderService orderService;

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(
      summary = "Record a sale with billing",
      description = "Creates a sales order and automatically generates an invoice")
  public ResponseEntity<OrderResponse> createSale(@RequestBody CreateOrderRequest request) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    Long userId = null;
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      userId = jwtAuth.getUserId();
    }
    Long tenantId = TenantContext.getTenantId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orderService.createSalesOrder(tenantId, request, Objects.requireNonNull(userId)));
  }
}
