package com.ims.tenant.controller;

import com.ims.model.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/audits")
@RequiredArgsConstructor
@Tag(name = "Tenant - Audit", description = "Tenant-specific activity logs")
@SecurityRequirement(name = "bearerAuth")
public class TenantAuditController {

  private final com.ims.shared.audit.AuditLogService auditLogService;

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get activity logs for current tenant")
  public ResponseEntity<Page<AuditLog>> getTenantLogs(Pageable pageable) {
    return ResponseEntity.ok(auditLogService.getTenantLogs(pageable));
  }
}
