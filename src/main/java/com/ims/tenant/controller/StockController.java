package com.ims.tenant.controller;

import com.ims.dto.TransferOrderStatusRequest;
import com.ims.dto.request.StockInRequest;
import com.ims.model.StockMovement;
import com.ims.model.TransferOrder;
import com.ims.tenant.domain.warehouse.WarehouseProduct;
import com.ims.tenant.domain.warehouse.TransferOrderService;
import com.ims.tenant.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenant/stock")
@RequiredArgsConstructor
@Tag(name = "Tenant - Stock", description = "Stock management")
@SecurityRequirement(name = "bearerAuth")
public class StockController {

  private final StockService stockService;
  private final TransferOrderService transferOrderService;

  @PostMapping("/transfer")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Transfer stock between locations")
  public ResponseEntity<TransferOrder> transfer(@RequestBody Map<String, Object> body) {
    Long userId = extractUserId();
    TransferOrder result = Objects.requireNonNull(transferOrderService.createTransfer(body, userId));
    return ResponseEntity.ok(result);
  }

  @GetMapping("/by-location")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "List products at storage location")
  public ResponseEntity<Page<WarehouseProduct>> getByLocation(
      @RequestParam String location, Pageable pageable) {
    return ResponseEntity.ok(Objects.requireNonNull(stockService.getProductsByLocation(location, pageable)));
  }

  @GetMapping("/transfers")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "List all transfer orders")
  public ResponseEntity<Page<TransferOrder>> getTransfers(Pageable pageable) {
    return ResponseEntity.ok(Objects.requireNonNull(stockService.getTransferOrders(pageable)));
  }

  @GetMapping("/transfers/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Get transfer order detail")
  public ResponseEntity<TransferOrder> getTransferById(@PathVariable Long id) {
    return ResponseEntity.ok(Objects.requireNonNull(stockService.getTransferOrderById(id)));
  }

  @PatchMapping("/transfers/{id}/status")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Update transfer order status")
  public ResponseEntity<TransferOrder> updateTransferStatus(
      @PathVariable Long id, @RequestBody TransferOrderStatusRequest request) {
    Long userId = extractUserId();
    return ResponseEntity.ok(Objects.requireNonNull(stockService.updateTransferStatus(id, request, userId)));
  }

  @PostMapping("/in")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Record stock received")
  public ResponseEntity<Map<String, String>> stockIn(@RequestBody StockInRequest request) {
    Long userId = extractUserId();
    Long productId = request.getProductId();
    int quantity = request.getQuantity();
    String notes = request.getNotes() != null ? request.getNotes() : "";

    if (productId == null || quantity <= 0) {
      throw new IllegalArgumentException("productId and valid quantity are required");
    }

    stockService.stockIn(productId, quantity, notes, userId);
    return ResponseEntity.ok(Map.of("message", "Stock in recorded successfully"));
  }

  @PostMapping("/out")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Record stock issued")
  public ResponseEntity<Map<String, String>> stockOut(@RequestBody Map<String, Object> body) {
    Long userId = extractUserId();
    Object productIdObj = body.get("product_id");
    Object quantityObj = body.get("quantity");
    if (productIdObj == null || quantityObj == null) {
      throw new IllegalArgumentException("product_id and quantity are required");
    }
    Long productId = Long.valueOf(productIdObj.toString());
    int quantity = Integer.parseInt(quantityObj.toString());
    String notes = body.getOrDefault("notes", "").toString();

    stockService.stockOut(productId, quantity, notes, userId);
    return ResponseEntity.ok(Map.of("message", "Stock out recorded successfully"));
  }

  @PostMapping("/adjust")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Manual stock adjustment")
  public ResponseEntity<Map<String, String>> adjust(@RequestBody Map<String, Object> body) {
    Long userId = extractUserId();
    Object productIdObj = body.get("product_id");
    Object quantityObj = body.get("quantity");
    if (productIdObj == null || quantityObj == null) {
      throw new IllegalArgumentException("product_id and quantity are required");
    }
    Long productId = Long.valueOf(productIdObj.toString());
    int quantity = Integer.parseInt(quantityObj.toString());
    String notes = body.getOrDefault("notes", "").toString();

    stockService.stockAdjust(productId, quantity, notes, userId);
    return ResponseEntity.ok(Map.of("message", "Stock adjustment recorded"));
  }

  @GetMapping("/movements")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Stock movement log")
  public ResponseEntity<Page<StockMovement>> getMovements(Pageable pageable) {
    Page<StockMovement> result = stockService.getMovements(pageable);
    if (result == null) {
      throw new IllegalStateException("Stock movements not found");
    }
    return ResponseEntity.ok(result);
  }

  private Long extractUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof com.ims.shared.auth.JwtAuthenticationToken jwtAuth) {
      return jwtAuth.getUserId();
    }
    return null;
  }
}
