package com.ims.tenant.controller;

import com.ims.model.Supplier;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenant/suppliers")
@RequiredArgsConstructor
@Tag(name = "Tenant - Suppliers")
@SecurityRequirement(name = "bearerAuth")
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @RequiresRole({"ADMIN", "MANAGER"})
    @Operation(summary = "List suppliers")
    public ResponseEntity<Page<Supplier>> list(Pageable pageable) {
        return ResponseEntity.ok(supplierService.getSuppliers(pageable));
    }

    @PostMapping
    @RequiresRole({"ADMIN", "MANAGER"})
    @Operation(summary = "Create supplier")
    public ResponseEntity<Supplier> create(@RequestBody Supplier supplier) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierService.create(supplier));
    }

    @GetMapping("/{id}")
    @RequiresRole({"ADMIN", "MANAGER"})
    @Operation(summary = "Get supplier")
    public ResponseEntity<Supplier> get(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getById(id));
    }

    @PutMapping("/{id}")
    @RequiresRole({"ADMIN", "MANAGER"})
    @Operation(summary = "Update supplier")
    public ResponseEntity<Supplier> update(@PathVariable Long id, @RequestBody Supplier supplier) {
        return ResponseEntity.ok(supplierService.update(id, supplier));
    }

    @DeleteMapping("/{id}")
    @RequiresRole({"ADMIN"})
    @Operation(summary = "Delete supplier")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
