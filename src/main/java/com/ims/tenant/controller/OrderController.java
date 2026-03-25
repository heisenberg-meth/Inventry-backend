package com.ims.tenant.controller;

import com.ims.model.Order;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/tenant/orders")
@RequiredArgsConstructor
@Tag(name = "Tenant - Orders", description = "Order management")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

  private final OrderService orderService;

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "List all orders")
  public ResponseEntity<Page<Order>> getOrders(
      @RequestParam(required = false) String type, Pageable pageable) {
    if (type != null) {
      return ResponseEntity.ok(orderService.getOrdersByType(type, pageable));
    }
    return ResponseEntity.ok(orderService.getOrders(pageable));
  }

  @PostMapping("/purchase")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Create purchase order")
  public ResponseEntity<Map<String, Object>> createPurchaseOrder(
      @RequestBody Map<String, Object> request) {
    Long tenantId = TenantContext.get();
    Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orderService.createPurchaseOrder(tenantId, request, userId));
  }

  @PostMapping("/sale")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Create sale order")
  public ResponseEntity<Map<String, Object>> createSalesOrder(
      @RequestBody Map<String, Object> request) {
    Long tenantId = TenantContext.get();
    Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orderService.createSalesOrder(tenantId, request, userId));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Get order with items")
  public ResponseEntity<Map<String, Object>> getOrder(@PathVariable Long id) {
    return ResponseEntity.ok(orderService.getOrderWithItems(id));
  }

  @PatchMapping("/{id}/status")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Update order status")
  public ResponseEntity<Order> updateStatus(
      @PathVariable Long id, @RequestBody Map<String, String> body) {
    return ResponseEntity.ok(orderService.updateOrderStatus(id, body.get("status")));
  }
}
