package com.ims.platform.controller;

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
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/audit")
@RequiredArgsConstructor
@Tag(name = "Platform - Audit", description = "Global monitoring and troubleshooting")
@SecurityRequirement(name = "bearerAuth")
public class PlatformAuditController {

  private final AuditLogService auditLogService;

  @GetMapping("/logs")
  @RequiresRole({"ROOT", "PLATFORM_ADMIN"})
  @Operation(
      summary = "View aggregated audit logs",
      description = "Shows combined logs across all tenants for platform monitoring")
  public ResponseEntity<Page<AuditLogResponse>> getAggregatedLogs(@NonNull Pageable pageable) {
    return ResponseEntity.ok(auditLogService.getAllLogs(Objects.requireNonNull(pageable)));
  }
}
