package com.ims.tenant.controller;

import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.CustomerService;
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
@RequestMapping("/tenant/customers")
@RequiredArgsConstructor
@Tag(name = "Tenant - Customers")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

  private final CustomerService customerService;
  private final com.ims.tenant.service.CustomerImportService importService;
  private final com.ims.shared.utils.CsvExportService csvExportService;
  private final com.ims.tenant.repository.CustomerRepository customerRepository;

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "List customers")
  public @NonNull ResponseEntity<Page<com.ims.dto.response.CustomerResponse>> list(@NonNull Pageable pageable) {
    return ResponseEntity.ok(Objects.requireNonNull(customerService.getCustomers(pageable)));
  }

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Create customer")
  public @NonNull ResponseEntity<com.ims.dto.response.CustomerResponse> create(@jakarta.validation.Valid @RequestBody @NonNull com.ims.dto.request.CustomerRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(Objects.requireNonNull(customerService.create(request)));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Get customer")
  public @NonNull ResponseEntity<com.ims.dto.response.CustomerResponse> get(@PathVariable @NonNull Long id) {
    return ResponseEntity.ok(Objects.requireNonNull(customerService.getCustomerResponseById(id)));
  }

  @PutMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Update customer")
  public @NonNull ResponseEntity<com.ims.dto.response.CustomerResponse> update(@PathVariable @NonNull Long id, @jakarta.validation.Valid @RequestBody @NonNull com.ims.dto.request.CustomerRequest request) {
    return ResponseEntity.ok(Objects.requireNonNull(customerService.update(id, request)));
  }

  @DeleteMapping("/{id}")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Delete customer")
  public @NonNull ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
    customerService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/ledger")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Get full ledger for customer")
  public ResponseEntity<java.util.Map<String, Object>> getCustomerLedger(@PathVariable @NonNull Long id) {
    return ResponseEntity.ok(customerService.getCustomerLedger(id));
  }

  @PostMapping("/bulk-import")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Bulk import customers via CSV")
  public ResponseEntity<java.util.Map<String, Object>> bulkImport(
      @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
      @org.springframework.web.bind.annotation.RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
    return ResponseEntity.ok(importService.importCustomers(file, dryRun));
  }

  @GetMapping("/export")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Export customers as CSV")
  public ResponseEntity<String> export() {
    var data = customerRepository.findAll().stream().map(c -> {
      java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
      map.put("ID", c.getId());
      map.put("Name", c.getName());
      map.put("Phone", c.getPhone());
      map.put("Email", c.getEmail());
      map.put("Address", c.getAddress());
      map.put("GSTIN", c.getGstin());
      return map;
    }).collect(java.util.stream.Collectors.toList());

    String csv = csvExportService.exportToCsv(java.util.List.of("ID", "Name", "Phone", "Email", "Address", "GSTIN"), data);
    return ResponseEntity.ok()
        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=customers.csv")
        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv")
        .body(csv);
  }
}
