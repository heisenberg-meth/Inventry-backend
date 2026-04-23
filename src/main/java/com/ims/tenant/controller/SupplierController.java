package com.ims.tenant.controller;

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
@RequestMapping("/tenant/suppliers")
@RequiredArgsConstructor
@Tag(name = "Tenant - Suppliers")
@SecurityRequirement(name = "bearerAuth")
public class SupplierController {

  private final SupplierService supplierService;
  private final com.ims.tenant.service.SupplierImportService importService;
  private final com.ims.shared.utils.CsvExportService csvExportService;
  private final com.ims.tenant.repository.SupplierRepository supplierRepository;

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "List suppliers")
  public @NonNull ResponseEntity<Page<com.ims.dto.response.SupplierResponse>> list(@NonNull Pageable pageable) {
    return ResponseEntity.ok(Objects.requireNonNull(supplierService.getSuppliers(pageable)));
  }

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Create supplier")
  public @NonNull ResponseEntity<com.ims.dto.response.SupplierResponse> create(@jakarta.validation.Valid @RequestBody @NonNull com.ims.dto.request.SupplierRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(Objects.requireNonNull(supplierService.create(request)));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Get supplier")
  public @NonNull ResponseEntity<com.ims.dto.response.SupplierResponse> get(@PathVariable @NonNull Long id) {
    return ResponseEntity.ok(Objects.requireNonNull(supplierService.getSupplierResponseById(id)));
  }

  @PutMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Update supplier")
  public @NonNull ResponseEntity<com.ims.dto.response.SupplierResponse> update(@PathVariable @NonNull Long id, @jakarta.validation.Valid @RequestBody @NonNull com.ims.dto.request.SupplierRequest request) {
    return ResponseEntity.ok(Objects.requireNonNull(supplierService.update(id, request)));
  }

  @DeleteMapping("/{id}")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Delete supplier")
  public @NonNull ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
    supplierService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/ledger")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Get full ledger for supplier")
  public ResponseEntity<java.util.Map<String, Object>> getSupplierLedger(@PathVariable @NonNull Long id) {
    return ResponseEntity.ok(supplierService.getSupplierLedger(id));
  }

  @PostMapping("/bulk-import")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Bulk import suppliers via CSV")
  public ResponseEntity<java.util.Map<String, Object>> bulkImport(
      @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
      @org.springframework.web.bind.annotation.RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
    return ResponseEntity.ok(importService.importSuppliers(file, dryRun));
  }

  @GetMapping("/export")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Export suppliers as CSV")
  public ResponseEntity<String> export() {
    var data = supplierRepository.findAll().stream().map(s -> {
      java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
      map.put("ID", s.getId());
      map.put("Name", s.getName());
      map.put("Phone", s.getPhone());
      map.put("Email", s.getEmail());
      map.put("Address", s.getAddress());
      map.put("GSTIN", s.getGstin());
      return map;
    }).collect(java.util.stream.Collectors.toList());

    String csv = csvExportService.exportToCsv(java.util.List.of("ID", "Name", "Phone", "Email", "Address", "GSTIN"), data);
    return ResponseEntity.ok()
        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=suppliers.csv")
        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv")
        .body(csv);
  }
}
