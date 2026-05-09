package com.ims.tenant.controller;

import com.ims.order.dto.CreateOrderRequest;
import com.ims.order.dto.OrderResponse;
import com.ims.order.entity.OrderStatus;
import com.ims.order.entity.OrderType;
import com.ims.shared.auth.TenantContext;
import com.ims.model.Order;
import com.ims.tenant.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/orders")
@RequiredArgsConstructor
@Tag(name = "Tenant - Orders", description = "Order management")
@SecurityRequirement(name = "bearerAuth")

public class OrderController {

  private final OrderService orderService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "List all orders")
  public ResponseEntity<Page<Order>> getOrders(
      @RequestParam(required = false) OrderType type, Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    if (type != null) {
      return ResponseEntity.ok(orderService.getOrdersByType(tenantId, type, pageable));
    }
    return ResponseEntity.ok(orderService.getOrders(tenantId, pageable));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Create order (SALE or PURCHASE)")
  public ResponseEntity<OrderResponse> createOrder(
      @Valid @RequestBody CreateOrderRequest request) {
    Long userId = extractUserId();
    Long tenantId = TenantContext.getTenantId();

    if (request.getType() == OrderType.SALE) {
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(orderService.createSalesOrder(tenantId, request, userId));
    } else {
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(orderService.createPurchaseOrder(tenantId, request, userId));
    }
  }

  @PostMapping("/return")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Create return order")
  public ResponseEntity<Order> createReturnOrder(@RequestBody Map<String, Object> request) {
    Long tenantId = TenantContext.getTenantId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orderService.createReturnOrder(tenantId, request, extractUserId()));
  }

  @PostMapping("/{id}/confirm")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Confirm order and deduct stock (for sales)")
  public ResponseEntity<com.ims.model.Order> confirmOrder(@PathVariable Long id) {
    Long userId = extractUserId();
    Long tenantId = TenantContext.getTenantId();
    return ResponseEntity.ok(orderService.confirmOrder(id, tenantId, userId));
  }

  @PostMapping("/{id}/ship")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Mark order as shipped")
  public ResponseEntity<com.ims.model.Order> shipOrder(@PathVariable Long id) {
    Long userId = extractUserId();
    Long tenantId = TenantContext.getTenantId();
    return ResponseEntity.ok(orderService.shipOrder(id, tenantId, userId));
  }

  @PostMapping("/{id}/complete")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Complete order and add stock (for purchases)")
  public ResponseEntity<com.ims.model.Order> completeOrder(@PathVariable Long id) {
    Long userId = extractUserId();
    Long tenantId = TenantContext.getTenantId();
    return ResponseEntity.ok(orderService.completeOrder(id, tenantId, userId));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Cancel order and revert stock if confirmed")
  public ResponseEntity<com.ims.model.Order> cancelOrder(@PathVariable Long id) {
    Long userId = extractUserId();
    Long tenantId = TenantContext.getTenantId();
    return ResponseEntity.ok(orderService.cancelOrder(id, tenantId, userId));
  }

  private Long extractUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof com.ims.shared.auth.JwtAuthenticationToken jwtAuth) {
      return jwtAuth.getUserId();
    }
    return null;
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Get order detail with items")
  public ResponseEntity<com.ims.order.dto.OrderResponse> getOrder(@PathVariable Long id) {
    Long tenantId = TenantContext.getTenantId();
    return ResponseEntity.ok(orderService.getOrderWithItems(id, tenantId));
  }

  @GetMapping("/{id}/pdf")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Download order summary as PDF")
  public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
    Long tenantId = TenantContext.getTenantId();
    byte[] pdf = orderService.generateOrderPdf(id, tenantId);
    return ResponseEntity.ok()
        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=order-" + id + ".pdf")
        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/pdf")
        .body(pdf);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Update order status")
  public ResponseEntity<Order> updateStatus(
      @PathVariable Long id, @RequestBody Map<String, String> body) {
    Long tenantId = TenantContext.getTenantId();
    String statusStr = Objects.requireNonNull(body.get("status"));
    return ResponseEntity.ok(orderService.updateOrderStatus(id, tenantId, OrderStatus.valueOf(statusStr)));
  }
}
