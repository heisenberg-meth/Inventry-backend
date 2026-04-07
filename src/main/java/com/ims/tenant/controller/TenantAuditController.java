package com.ims.tenant.controller;

import com.ims.model.AuditLog;
import com.ims.shared.audit.AuditLogRepository;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.rbac.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/audits")
@RequiredArgsConstructor
@Tag(name = "Tenant - Audit", description = "Tenant-specific activity logs")
@SecurityRequirement(name = "bearerAuth")
public class TenantAuditController {

  private final AuditLogRepository auditLogRepository;

  @GetMapping
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Get activity logs for current tenant")
  public ResponseEntity<Page<AuditLog>> getTenantLogs(Pageable pageable) {
    Long tenantId = getTenantId();
    return ResponseEntity.ok(auditLogRepository.findByTenantId(tenantId, pageable));
  }

  private Long getTenantId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
      return Objects.requireNonNull(details.getTenantId());
    }
    throw new IllegalStateException("Tenant ID missing from security context");
  }
}
