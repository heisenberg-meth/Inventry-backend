package com.ims.tenant.controller;

import com.ims.model.Order;
import com.ims.tenant.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/tenant/orders")
@RequiredArgsConstructor
@Tag(name = "Tenant - Orders", description = "Order management")
@SecurityRequirement(name = "bearerAuth")

public class OrderController {

  private final OrderService orderService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "List all orders")
  public ResponseEntity<Page<Order>> getOrders(
      @RequestParam(required = false) String type, Pageable pageable) {
    if (type != null) {
      return ResponseEntity.ok(orderService.getOrdersByType(type, pageable));
    }
    return ResponseEntity.ok(orderService.getOrders(pageable));
  }

  @PostMapping("/purchase")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Create purchase order")
  public ResponseEntity<Map<String, Object>> createPurchaseOrder(
      @RequestBody Map<String, Object> request) {
    Long userId = extractUserId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orderService.createPurchaseOrder(request, userId));
  }

  @PostMapping("/sale")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Create sale order")
  public ResponseEntity<Map<String, Object>> createSalesOrder(
      @RequestBody Map<String, Object> request) {
    Long userId = extractUserId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orderService.createSalesOrder(request, userId));
  }

  @PostMapping("/return")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Create return order")
  public ResponseEntity<Order> createReturnOrder(@RequestBody Map<String, Object> request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createReturnOrder(request, extractUserId()));
  }

  @PostMapping("/{id}/confirm")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Confirm order and deduct stock (for sales)")
  public ResponseEntity<Order> confirmOrder(@PathVariable Long id) {
    Long userId = extractUserId();
    return ResponseEntity.ok(orderService.confirmOrder(id, userId));
  }

  @PostMapping("/{id}/ship")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Mark order as shipped")
  public ResponseEntity<Order> shipOrder(@PathVariable Long id) {
    Long userId = extractUserId();
    return ResponseEntity.ok(orderService.shipOrder(id, userId));
  }

  @PostMapping("/{id}/complete")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Complete order and add stock (for purchases)")
  public ResponseEntity<Order> completeOrder(@PathVariable Long id) {
    Long userId = extractUserId();
    return ResponseEntity.ok(orderService.completeOrder(id, userId));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Cancel order and revert stock if confirmed")
  public ResponseEntity<Order> cancelOrder(@PathVariable Long id) {
    Long userId = extractUserId();
    return ResponseEntity.ok(orderService.cancelOrder(id, userId));
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
  public ResponseEntity<Map<String, Object>> getOrder(@PathVariable Long id) {
    return ResponseEntity.ok(orderService.getOrderWithItems(id));
  }

  @GetMapping("/{id}/pdf")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Download order summary as PDF")
  public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
    byte[] pdf = orderService.generateOrderPdf(id);
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
    return ResponseEntity.ok(orderService.updateOrderStatus(id, Objects.requireNonNull(body.get("status"))));
  }
}
