package com.ims.tenant.controller;

import com.ims.dto.TransferOrderStatusRequest;
import com.ims.model.StockMovement;
import com.ims.model.TransferOrder;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.domain.warehouse.WarehouseProduct;
import com.ims.tenant.domain.warehouse.TransferOrderService;
import com.ims.tenant.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import java.util.Objects;
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
@RequestMapping("/api/tenant/stock")
@RequiredArgsConstructor
@Tag(name = "Tenant - Stock", description = "Stock management")
@SecurityRequirement(name = "bearerAuth")
public class StockController {

  private final StockService stockService;
  private final TransferOrderService transferOrderService;

  @PostMapping("/transfer")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Transfer stock between locations")
  public @NonNull ResponseEntity<TransferOrder> transfer(@RequestBody @NonNull Map<String, Object> body) {
    Long userId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
    TransferOrder result = Objects.requireNonNull(transferOrderService.createTransfer(body, Objects.requireNonNull(userId)));
    return ResponseEntity.ok(result);
  }

  @GetMapping("/by-location")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "List products at storage location")
  public @NonNull ResponseEntity<Page<WarehouseProduct>> getByLocation(
      @RequestParam @NonNull String location, @NonNull Pageable pageable) {
    return ResponseEntity.ok(Objects.requireNonNull(stockService.getProductsByLocation(location, pageable)));
  }

  @GetMapping("/transfers")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "List all transfer orders")
  public @NonNull ResponseEntity<Page<TransferOrder>> getTransfers(@NonNull Pageable pageable) {
    return ResponseEntity.ok(Objects.requireNonNull(stockService.getTransferOrders(pageable)));
  }

  @GetMapping("/transfers/{id}")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Get transfer order detail")
  public @NonNull ResponseEntity<TransferOrder> getTransferById(@PathVariable @NonNull Long id) {
    return ResponseEntity.ok(Objects.requireNonNull(stockService.getTransferOrderById(id)));
  }

  @PatchMapping("/transfers/{id}/status")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Update transfer order status")
  public @NonNull ResponseEntity<TransferOrder> updateTransferStatus(
      @PathVariable @NonNull Long id, @RequestBody @NonNull TransferOrderStatusRequest request) {
    return ResponseEntity.ok(Objects.requireNonNull(stockService.updateTransferStatus(id, request)));
  }

  @PostMapping("/in")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Record stock received")
  public @NonNull ResponseEntity<Map<String, String>> stockIn(@RequestBody @NonNull Map<String, Object> body) {
    Long userId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
    Long productId = Long.valueOf(Objects.requireNonNull(body.get("product_id")).toString());
    int quantity = Integer.parseInt(Objects.requireNonNull(body.get("quantity")).toString());
    String notes = body.getOrDefault("notes", "").toString();

    stockService.stockIn(Objects.requireNonNull(productId), quantity, notes, Objects.requireNonNull(userId));
    return ResponseEntity.ok(Objects.requireNonNull(Map.of("message", "Stock in recorded successfully")));
  }

  @PostMapping("/out")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Record stock issued")
  public @NonNull ResponseEntity<Map<String, String>> stockOut(@RequestBody @NonNull Map<String, Object> body) {
    Long userId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
    Long productId = Long.valueOf(Objects.requireNonNull(body.get("product_id")).toString());
    int quantity = Integer.parseInt(Objects.requireNonNull(body.get("quantity")).toString());
    String notes = body.getOrDefault("notes", "").toString();

    stockService.stockOut(Objects.requireNonNull(productId), quantity, notes, Objects.requireNonNull(userId));
    return ResponseEntity.ok(Objects.requireNonNull(Map.of("message", "Stock out recorded successfully")));
  }

  @PostMapping("/adjust")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Manual stock adjustment")
  public @NonNull ResponseEntity<Map<String, String>> adjust(@RequestBody @NonNull Map<String, Object> body) {
    Long userId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
    Long productId = Long.valueOf(Objects.requireNonNull(body.get("product_id")).toString());
    int quantity = Integer.parseInt(Objects.requireNonNull(body.get("quantity")).toString());
    String notes = body.getOrDefault("notes", "").toString();

    stockService.stockAdjust(Objects.requireNonNull(productId), quantity, notes, Objects.requireNonNull(userId));
    return ResponseEntity.ok(Objects.requireNonNull(Map.of("message", "Stock adjustment recorded")));
  }

  @GetMapping("/movements")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Stock movement log")
  public @NonNull ResponseEntity<Page<StockMovement>> getMovements(@NonNull Pageable pageable) {
    return ResponseEntity.ok(Objects.requireNonNull(stockService.getMovements(pageable)));
  }
}
