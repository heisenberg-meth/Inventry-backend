package com.ims.tenant.controller;

import com.ims.dto.request.CustomerRequest;
import com.ims.dto.response.CustomerResponse;
import com.ims.shared.rbac.RequiresRole;
import com.ims.shared.utils.CsvExportService;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.service.CustomerImportService;
import com.ims.tenant.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tenant/customers")
@RequiredArgsConstructor
@Tag(name = "Tenant - Customers")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

  private final CustomerService customerService;
  private final CustomerImportService importService;
  private final CsvExportService csvExportService;
  private final CustomerRepository customerRepository;

  @GetMapping
  @RequiresRole({ "TENANT_ADMIN", "BUSINESS_MANAGER" })
  @Operation(summary = "List customers")
  public ResponseEntity<Page<CustomerResponse>> list(
      Pageable pageable) {
    return ResponseEntity.ok(Objects.requireNonNull(customerService.getCustomers(pageable)));
  }

  @PostMapping
  @RequiresRole({ "TENANT_ADMIN", "BUSINESS_MANAGER" })
  @Operation(summary = "Create customer")
  public ResponseEntity<CustomerResponse> create(
      @Valid @RequestBody CustomerRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Objects.requireNonNull(customerService.create(request)));
  }

  @GetMapping("/{id}")
  @RequiresRole({ "TENANT_ADMIN", "BUSINESS_MANAGER" })
  @Operation(summary = "Get customer")
  public ResponseEntity<CustomerResponse> get(@PathVariable long id) {
    return ResponseEntity.ok(Objects.requireNonNull(customerService.getCustomerResponseById(id)));
  }

  @PutMapping("/{id}")
  @RequiresRole({ "TENANT_ADMIN", "BUSINESS_MANAGER" })
  @Operation(summary = "Update customer")
  public ResponseEntity<CustomerResponse> update(
      @PathVariable long id,
      @Valid @RequestBody CustomerRequest request) {
    return ResponseEntity.ok(Objects.requireNonNull(customerService.update(id, request)));
  }

  @DeleteMapping("/{id}")
  @RequiresRole({ "TENANT_ADMIN" })
  @Operation(summary = "Delete customer")
  public ResponseEntity<Void> delete(@PathVariable long id) {
    customerService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/ledger")
  @RequiresRole({ "TENANT_ADMIN", "BUSINESS_MANAGER" })
  @Operation(summary = "Get full ledger for customer")
  public ResponseEntity<Map<String, Object>> getCustomerLedger(@PathVariable long id) {
    return ResponseEntity.ok(customerService.getCustomerLedger(id));
  }

  @PostMapping("/bulk-import")
  @RequiresRole({ "TENANT_ADMIN", "BUSINESS_MANAGER" })
  @Operation(summary = "Bulk import customers via CSV")
  public ResponseEntity<Map<String, Object>> bulkImport(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
    return ResponseEntity.ok(importService.importCustomers(Objects.requireNonNull(file), dryRun));
  }

  @GetMapping("/export")
  @RequiresRole({ "TENANT_ADMIN", "BUSINESS_MANAGER" })
  @Operation(summary = "Export customers as CSV")
  public ResponseEntity<String> export() {
    var data = customerRepository.findAll().stream()
        .map(
            c -> {
              Map<String, Object> map = new LinkedHashMap<>();
              map.put("ID", c.getId());
              map.put("Name", c.getName());
              map.put("Phone", c.getPhone());
              map.put("Email", c.getEmail());
              map.put("Address", c.getAddress());
              map.put("GSTIN", c.getGstin());
              return map;
            })
        .collect(Collectors.toList());

    String csv = csvExportService.exportToCsv(
        List.of("ID", "Name", "Phone", "Email", "Address", "GSTIN"), data);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=customers.csv")
        .header(HttpHeaders.CONTENT_TYPE, "text/csv")
        .body(csv);
  }
}
