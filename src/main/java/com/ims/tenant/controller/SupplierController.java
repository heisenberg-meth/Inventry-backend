package com.ims.tenant.controller;

import com.ims.dto.request.SupplierRequest;
import com.ims.dto.response.SupplierResponse;
import com.ims.shared.rbac.RequiresRole;
import com.ims.shared.utils.CsvExportService;
import com.ims.tenant.repository.SupplierRepository;
import com.ims.tenant.service.SupplierImportService;
import com.ims.tenant.service.SupplierService;
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
@RequestMapping("/api/v1/tenant/suppliers")
@RequiredArgsConstructor
@Tag(name = "Tenant - Suppliers")
@SecurityRequirement(name = "bearerAuth")
public class SupplierController {

  private final SupplierService supplierService;
  private final SupplierImportService importService;
  private final CsvExportService csvExportService;
  private final SupplierRepository supplierRepository;

  @GetMapping
  @RequiresRole({ "ADMIN", "MANAGER" })
  @Operation(summary = "List suppliers")
  public ResponseEntity<Page<SupplierResponse>> list(
      Pageable pageable) {
    return ResponseEntity.ok(Objects.requireNonNull(supplierService.getSuppliers(pageable)));
  }

  @PostMapping
  @RequiresRole({ "ADMIN", "MANAGER" })
  @Operation(summary = "Create supplier")
  public ResponseEntity<SupplierResponse> create(
      @Valid @RequestBody SupplierRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Objects.requireNonNull(supplierService.create(request)));
  }

  @GetMapping("/{id}")
  @RequiresRole({ "ADMIN", "MANAGER" })
  @Operation(summary = "Get supplier")
  public ResponseEntity<SupplierResponse> get(@PathVariable long id) {
    return ResponseEntity.ok(Objects.requireNonNull(supplierService.getSupplierResponseById(id)));
  }

  @PutMapping("/{id}")
  @RequiresRole({ "ADMIN", "MANAGER" })
  @Operation(summary = "Update supplier")
  public ResponseEntity<SupplierResponse> update(
      @PathVariable long id,
      @Valid @RequestBody SupplierRequest request) {
    return ResponseEntity.ok(Objects.requireNonNull(supplierService.update(id, request)));
  }

  @DeleteMapping("/{id}")
  @RequiresRole({ "ADMIN" })
  @Operation(summary = "Delete supplier")
  public ResponseEntity<Void> delete(@PathVariable long id) {
    supplierService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/ledger")
  @RequiresRole({ "ADMIN", "MANAGER" })
  @Operation(summary = "Get full ledger for supplier")
  public ResponseEntity<Map<String, Object>> getSupplierLedger(@PathVariable long id) {
    return ResponseEntity.ok(supplierService.getSupplierLedger(id));
  }

  @PostMapping("/bulk-import")
  @RequiresRole({ "ADMIN", "MANAGER" })
  @Operation(summary = "Bulk import suppliers via CSV")
  public ResponseEntity<Map<String, Object>> bulkImport(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
    return ResponseEntity.ok(importService.importSuppliers(file, dryRun));
  }

  @GetMapping("/export")
  @RequiresRole({ "ADMIN", "MANAGER" })
  @Operation(summary = "Export suppliers as CSV")
  public ResponseEntity<String> export() {
    var data = supplierRepository.findAll().stream()
        .map(
            s -> {
              Map<String, Object> map = new LinkedHashMap<>();
              map.put("ID", s.getId());
              map.put("Name", s.getName());
              map.put("Phone", s.getPhone());
              map.put("Email", s.getEmail());
              map.put("Address", s.getAddress());
              map.put("GSTIN", s.getGstin());
              return map;
            })
        .collect(Collectors.toList());

    String csv = csvExportService.exportToCsv(
        List.of("ID", "Name", "Phone", "Email", "Address", "GSTIN"), data);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=suppliers.csv")
        .header(HttpHeaders.CONTENT_TYPE, "text/csv")
        .body(csv);
  }
}
