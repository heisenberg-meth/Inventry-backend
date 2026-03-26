package com.ims.tenant.controller;

import com.ims.dto.CreateInvoiceRequest;
import com.ims.dto.InvoiceStatusRequest;
import com.ims.model.Invoice;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/invoices")
@RequiredArgsConstructor
@Tag(name = "Tenant - Invoices", description = "Invoice management")
@SecurityRequirement(name = "bearerAuth")
public class InvoiceController {

  private final InvoiceService invoiceService;

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "List invoices")
  public ResponseEntity<Page<Invoice>> getInvoices(Pageable pageable) {
    return ResponseEntity.ok(invoiceService.getInvoices(pageable));
  }

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Manually generate invoice from order")
  public ResponseEntity<Invoice> createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.createManual(request));
  }

  @PatchMapping("/{id}/status")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Update invoice status")
  public ResponseEntity<Invoice> updateStatus(
      @PathVariable Long id, @Valid @RequestBody InvoiceStatusRequest request) {
    return ResponseEntity.ok(invoiceService.updateStatus(id, request));
  }

  @GetMapping("/{id}/pdf")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Download invoice PDF")
  public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
    byte[] pdf = invoiceService.generatePdf(id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-" + id + ".pdf")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
  }
}
