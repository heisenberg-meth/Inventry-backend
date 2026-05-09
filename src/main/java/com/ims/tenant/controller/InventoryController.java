package com.ims.tenant.controller;

import com.ims.dto.InventoryAdjustRequest;
import com.ims.dto.InventoryResponse;
import com.ims.dto.StockReservationRequest;
import com.ims.dto.StockReservationResponse;
import com.ims.shared.auth.JwtAuthenticationToken;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/inventory")
@RequiredArgsConstructor
@Tag(
    name = "Tenant - Inventory",
    description = "Inventory management with concurrency-safe operations")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

  private final InventoryService inventoryService;

  @GetMapping("/product/{productId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Get inventory for a specific product")
  public ResponseEntity<InventoryResponse> getByProductId(@PathVariable Long productId) {
    return ResponseEntity.ok(
        inventoryService.getInventoryByProductId(TenantContext.getTenantId(), productId));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "List all inventories")
  public ResponseEntity<Page<InventoryResponse>> getAll(Pageable pageable) {
    return ResponseEntity.ok(
        inventoryService.getAllInventories(TenantContext.getTenantId(), pageable));
  }

  @GetMapping("/low-stock")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "List products below low stock threshold")
  public ResponseEntity<Page<InventoryResponse>> getLowStock(Pageable pageable) {
    return ResponseEntity.ok(
        inventoryService.getLowStockInventories(TenantContext.getTenantId(), pageable));
  }

  @GetMapping("/reorder")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "List products below reorder level")
  public ResponseEntity<Page<InventoryResponse>> getReorderLevel(Pageable pageable) {
    return ResponseEntity.ok(
        inventoryService.getReorderLevelInventories(TenantContext.getTenantId(), pageable));
  }

  @GetMapping("/available/{productId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Get available stock for a product")
  public ResponseEntity<Integer> getAvailableStock(@PathVariable Long productId) {
    return ResponseEntity.ok(
        inventoryService.getAvailableStock(TenantContext.getTenantId(), productId));
  }

  @PostMapping("/increase")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Increase stock (purchase/order received)")
  public ResponseEntity<InventoryResponse> increaseStock(
      @Valid @RequestBody InventoryAdjustRequest request) {
    Long userId = extractUserId();
    return ResponseEntity.ok(
        inventoryService.increaseStock(
            TenantContext.getTenantId(),
            request.getProductId(),
            request.getQuantity(),
            request.getNotes(),
            userId));
  }

  @PostMapping("/decrease")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Decrease stock (sale/issue)")
  public ResponseEntity<InventoryResponse> decreaseStock(
      @Valid @RequestBody InventoryAdjustRequest request) {
    Long userId = extractUserId();
    return ResponseEntity.ok(
        inventoryService.decreaseStock(
            TenantContext.getTenantId(),
            request.getProductId(),
            request.getQuantity(),
            request.getNotes(),
            userId));
  }

  @PostMapping("/adjust")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Manual stock adjustment (positive or negative)")
  public ResponseEntity<InventoryResponse> adjustStock(
      @Valid @RequestBody InventoryAdjustRequest request) {
    Long userId = extractUserId();
    return ResponseEntity.ok(
        inventoryService.adjustStock(
            TenantContext.getTenantId(),
            request.getProductId(),
            request.getQuantity(),
            request.getNotes(),
            userId));
  }

  @PostMapping("/reserve")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Reserve stock for checkout/payment flow")
  public ResponseEntity<StockReservationResponse> reserveStock(
      @Valid @RequestBody StockReservationRequest request) {
    Long userId = extractUserId();
    request.setUserId(userId);
    return ResponseEntity.ok(inventoryService.reserveStock(TenantContext.getTenantId(), request));
  }

  @PostMapping("/release")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Release a stock reservation")
  public ResponseEntity<InventoryResponse> releaseReservation(
      @RequestParam Long productId,
      @RequestParam Integer quantity,
      @RequestParam(required = false) String notes) {
    Long userId = extractUserId();
    return ResponseEntity.ok(
        inventoryService.releaseReservation(
            TenantContext.getTenantId(), productId, quantity, notes, userId));
  }

  @PostMapping("/fulfill")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Fulfill a reservation (finalize deduction)")
  public ResponseEntity<InventoryResponse> fulfillReservation(
      @RequestParam Long productId,
      @RequestParam Integer quantity,
      @RequestParam(required = false) String notes) {
    Long userId = extractUserId();
    return ResponseEntity.ok(
        inventoryService.fulfillReservation(
            TenantContext.getTenantId(), productId, quantity, notes, userId));
  }

  @PutMapping("/thresholds/{productId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Update low stock threshold and reorder level")
  public ResponseEntity<InventoryResponse> updateThresholds(
      @PathVariable Long productId,
      @RequestParam(required = false) Integer lowStockThreshold,
      @RequestParam(required = false) Integer reorderLevel) {
    return ResponseEntity.ok(
        inventoryService.updateThresholds(
            TenantContext.getTenantId(), productId, lowStockThreshold, reorderLevel));
  }

  private Long extractUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      return jwtAuth.getUserId();
    }
    return null;
  }
}
