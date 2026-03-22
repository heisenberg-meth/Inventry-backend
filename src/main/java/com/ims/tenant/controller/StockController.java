package com.ims.tenant.controller;

import com.ims.model.StockMovement;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tenant/stock")
@RequiredArgsConstructor
@Tag(name = "Tenant - Stock", description = "Stock management")
@SecurityRequirement(name = "bearerAuth")
public class StockController {

    private final StockService stockService;

    @PostMapping("/in")
    @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
    @Operation(summary = "Record stock received")
    public ResponseEntity<Map<String, String>> stockIn(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContext.get();
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long productId = Long.valueOf(body.get("product_id").toString());
        int quantity = Integer.parseInt(body.get("quantity").toString());
        String notes = body.getOrDefault("notes", "").toString();

        stockService.stockIn(tenantId, productId, quantity, notes, userId);
        return ResponseEntity.ok(Map.of("message", "Stock in recorded successfully"));
    }

    @PostMapping("/out")
    @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
    @Operation(summary = "Record stock issued")
    public ResponseEntity<Map<String, String>> stockOut(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContext.get();
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long productId = Long.valueOf(body.get("product_id").toString());
        int quantity = Integer.parseInt(body.get("quantity").toString());
        String notes = body.getOrDefault("notes", "").toString();

        stockService.stockOut(tenantId, productId, quantity, notes, userId);
        return ResponseEntity.ok(Map.of("message", "Stock out recorded successfully"));
    }

    @PostMapping("/adjust")
    @RequiresRole({"ADMIN", "MANAGER"})
    @Operation(summary = "Manual stock adjustment")
    public ResponseEntity<Map<String, String>> adjust(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContext.get();
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long productId = Long.valueOf(body.get("product_id").toString());
        int quantity = Integer.parseInt(body.get("quantity").toString());
        String notes = body.getOrDefault("notes", "").toString();

        stockService.stockAdjust(tenantId, productId, quantity, notes, userId);
        return ResponseEntity.ok(Map.of("message", "Stock adjustment recorded"));
    }

    @GetMapping("/movements")
    @RequiresRole({"ADMIN", "MANAGER"})
    @Operation(summary = "Stock movement log")
    public ResponseEntity<Page<StockMovement>> getMovements(Pageable pageable) {
        return ResponseEntity.ok(stockService.getMovements(pageable));
    }
}
