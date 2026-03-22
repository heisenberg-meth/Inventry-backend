package com.ims.tenant.controller;

import com.ims.model.Invoice;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{id}/pdf")
    @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
    @Operation(summary = "Download invoice PDF")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        Long tenantId = TenantContext.get();
        byte[] pdf = invoiceService.generatePdf(id, tenantId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
