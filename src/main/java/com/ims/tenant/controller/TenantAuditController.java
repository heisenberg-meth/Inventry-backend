package com.ims.tenant.controller;

import com.ims.dto.response.AuditLogResponse;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.rbac.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/audits")
@RequiredArgsConstructor
@Tag(name = "Tenant - Audit", description = "Tenant-specific activity logs")
@SecurityRequirement(name = "bearerAuth")
public class TenantAuditController {

  private final AuditLogService auditLogService;

  @GetMapping
  @RequiresRole({ "ADMIN" })
  @Operation(summary = "Get activity logs for current tenant")
  public ResponseEntity<Page<AuditLogResponse>> getTenantLogs(Pageable pageable) {
    return ResponseEntity.ok(auditLogService.getTenantLogsAsDto(Objects.requireNonNull(pageable)));
  }

}
