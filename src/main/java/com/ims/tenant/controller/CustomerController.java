package com.ims.tenant.controller;

import com.ims.model.Order;
import com.ims.model.Customer;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.CustomerService;
import com.ims.tenant.service.OrderService;
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
@RequestMapping("/api/tenant/customers")
@RequiredArgsConstructor
@Tag(name = "Tenant - Customers")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

  private final CustomerService customerService;
  private final OrderService orderService;

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "List customers")
  public @NonNull ResponseEntity<Page<Customer>> list(@NonNull Pageable pageable) {
    return ResponseEntity.ok(Objects.requireNonNull(customerService.getCustomers(pageable)));
  }

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Create customer")
  public @NonNull ResponseEntity<Customer> create(@RequestBody @NonNull Customer customer) {
    return ResponseEntity.status(HttpStatus.CREATED).body(Objects.requireNonNull(customerService.create(customer)));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Get customer")
  public @NonNull ResponseEntity<Customer> get(@PathVariable @NonNull Long id) {
    return ResponseEntity.ok(Objects.requireNonNull(customerService.getById(id)));
  }

  @PutMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Update customer")
  public @NonNull ResponseEntity<Customer> update(@PathVariable @NonNull Long id, @RequestBody @NonNull Customer customer) {
    return ResponseEntity.ok(Objects.requireNonNull(customerService.update(id, customer)));
  }

  @DeleteMapping("/{id}")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Delete customer")
  public @NonNull ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
    customerService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/orders")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Get order history for customer")
  public ResponseEntity<Page<Order>> getCustomerOrders(
      @PathVariable @NonNull Long id, @NonNull Pageable pageable) {
    return ResponseEntity.ok(orderService.getOrdersByCustomer(id, pageable));
  }
}
