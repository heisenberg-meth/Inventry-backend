package com.ims.tenant.controller;

import com.ims.model.Order;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.dto.OrderRequest;
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
import org.springframework.lang.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/tenant/orders")
@RequiredArgsConstructor
@Tag(name = "Tenant - Orders", description = "Order management")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

  private final OrderService orderService;

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "List all orders")
  public ResponseEntity<Page<Order>> getOrders(
      @RequestParam(required = false) String type, @NonNull Pageable pageable) {
    if (type != null) {
      return ResponseEntity.ok(orderService.getOrdersByType(type, pageable));
    }
    return ResponseEntity.ok(orderService.getOrders(pageable));
  }

  @PostMapping("/purchase")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Create purchase order")
  public ResponseEntity<Order> createPurchaseOrder(
      @Valid @RequestBody OrderRequest request) {
    Long userId = extractUserId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orderService.createPurchaseOrder(request, userId));
  }

  @PostMapping("/sale")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Create sale order")
  public ResponseEntity<Order> createSalesOrder(
      @Valid @RequestBody OrderRequest request) {
    Long userId = extractUserId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orderService.createSalesOrder(request, userId));
  }

  @PostMapping("/return")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Create return order")
  public ResponseEntity<Order> createReturnOrder(@Valid @RequestBody OrderRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createReturnOrder(request, extractUserId()));
  }

  @PostMapping("/{id}/confirm")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Confirm order and deduct stock (for sales)")
  public ResponseEntity<Order> confirmOrder(@NonNull @PathVariable Long id) {
    Long userId = extractUserId();
    return ResponseEntity.ok(orderService.confirmOrder(id, userId));
  }

  @PostMapping("/{id}/ship")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Mark order as shipped")
  public ResponseEntity<Order> shipOrder(@NonNull @PathVariable Long id) {
    Long userId = extractUserId();
    return ResponseEntity.ok(orderService.shipOrder(id, userId));
  }

  @PostMapping("/{id}/complete")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Complete order and add stock (for purchases)")
  public ResponseEntity<Order> completeOrder(@NonNull @PathVariable Long id) {
    Long userId = extractUserId();
    return ResponseEntity.ok(orderService.completeOrder(id, userId));
  }

  @PostMapping("/{id}/cancel")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Cancel order and revert stock if confirmed")
  public ResponseEntity<Order> cancelOrder(@NonNull @PathVariable Long id) {
    Long userId = extractUserId();
    return ResponseEntity.ok(orderService.cancelOrder(id, userId));
  }

  private @NonNull Long extractUserId() {
    return (Long) Objects.requireNonNull(Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal());
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Get order detail with items")
  public ResponseEntity<Map<String, Object>> getOrder(@PathVariable @NonNull Long id) {
    return ResponseEntity.ok(orderService.getOrderWithItems(id));
  }

  @GetMapping("/{id}/pdf")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Download order summary as PDF")
  public ResponseEntity<byte[]> downloadPdf(@PathVariable @NonNull Long id) {
    byte[] pdf = orderService.generateOrderPdf(id);
    return ResponseEntity.ok()
        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=order-" + id + ".pdf")
        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/pdf")
        .body(pdf);
  }

  @PatchMapping("/{id}/status")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Update order status")
  public ResponseEntity<Order> updateStatus(
      @NonNull @PathVariable Long id, @NonNull @RequestBody Map<String, String> body) {
    return ResponseEntity.ok(orderService.updateOrderStatus(id, Objects.requireNonNull(body.get("status"))));
  }
}
