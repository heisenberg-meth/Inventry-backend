package com.ims.tenant.controller;

import com.ims.model.TransferOrder;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.domain.warehouse.TransferOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenant/transfers")
@RequiredArgsConstructor
@Tag(name = "Tenant - Warehouse Transfers")
@SecurityRequirement(name = "bearerAuth")
public class TransferOrderController {

  private final TransferOrderService transferOrderService;

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "List warehouse transfers")
  public ResponseEntity<Page<TransferOrder>> list(@NonNull Pageable pageable) {
    return ResponseEntity.ok(transferOrderService.getTransfers(pageable));
  }

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Create warehouse transfer")
  public ResponseEntity<TransferOrder> create(
      @NonNull @RequestBody Map<String, Object> request,
      @NonNull @com.ims.shared.auth.CurrentUser Long userId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(transferOrderService.createTransfer(request, userId));
  }

  @PatchMapping("/{id}/status")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Update transfer status (SHIPPED/RECEIVED/CANCELLED)")
  public ResponseEntity<TransferOrder> updateStatus(
      @PathVariable Long id,
      @RequestBody Map<String, String> body,
      @NonNull @com.ims.shared.auth.CurrentUser Long userId) {
    String status = body.get("status");
    if (status == null) throw new IllegalArgumentException("Status is required");
    return ResponseEntity.ok(transferOrderService.updateStatus(id, status, userId));
  }
}
