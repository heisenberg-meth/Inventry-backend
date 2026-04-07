package com.ims.tenant.controller;

import com.ims.model.Order;
import com.ims.model.Supplier;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.OrderService;
import com.ims.tenant.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/suppliers")
@RequiredArgsConstructor
@Tag(name = "Tenant - Suppliers")
@SecurityRequirement(name = "bearerAuth")
public class SupplierController {

  private final SupplierService supplierService;
  private final OrderService orderService;

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "List suppliers")
  public @NonNull ResponseEntity<Page<Supplier>> list(@NonNull Pageable pageable) {
    return ResponseEntity.ok(Objects.requireNonNull(supplierService.getSuppliers(pageable)));
  }

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Create supplier")
  public @NonNull ResponseEntity<Supplier> create(@RequestBody @NonNull Supplier supplier) {
    return ResponseEntity.status(HttpStatus.CREATED).body(Objects.requireNonNull(supplierService.create(supplier)));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Get supplier")
  public @NonNull ResponseEntity<Supplier> get(@PathVariable @NonNull Long id) {
    return ResponseEntity.ok(Objects.requireNonNull(supplierService.getById(id)));
  }

  @PutMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Update supplier")
  public @NonNull ResponseEntity<Supplier> update(@PathVariable @NonNull Long id, @RequestBody @NonNull Supplier supplier) {
    return ResponseEntity.ok(Objects.requireNonNull(supplierService.update(id, supplier)));
  }

  @DeleteMapping("/{id}")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Delete supplier")
  public @NonNull ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
    supplierService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/orders")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Get order history for supplier")
  public ResponseEntity<Page<Order>> getSupplierOrders(
      @PathVariable @NonNull Long id, @NonNull Pageable pageable) {
    return ResponseEntity.ok(orderService.getOrdersBySupplier(id, pageable));
  }
}
