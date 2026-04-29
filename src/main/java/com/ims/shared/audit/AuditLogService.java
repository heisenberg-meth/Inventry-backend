package com.ims.shared.audit;

import com.ims.platform.service.SystemConfigService;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.model.AuditLog;
import com.ims.shared.auth.TenantContext;
import com.ims.dto.response.AuditLogResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;
  private final SystemConfigService systemConfigService;

  public void log(AuditAction action, Long tenantId, Long userId, String details) {
    log.info("AUDIT: action={} details={}", action, details);

    Long impersonatedBy = null;
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails detailsObj) {
      impersonatedBy = detailsObj.getImpersonatedBy();
    }

    AuditLog auditEntry = Objects.requireNonNull(
        AuditLog.builder()
            .tenantId(tenantId)
            .userId(userId)
            .action(Objects.requireNonNull(action.name()))
            .details(Objects.requireNonNull(details))
            .impersonatedBy(impersonatedBy)
            .build());

    auditLogRepository.save(auditEntry);
  }

  public void log(
      AuditAction action, Long tenantId, Long userId, Map<String, Object> metadata) {
    try {
      String details = new ObjectMapper().writeValueAsString(metadata);
      log(action, tenantId, userId, Objects.requireNonNull(details));
    } catch (Exception e) {
      log.error("Failed to serialize audit metadata", e);
      log(action, tenantId, userId, Objects.requireNonNull(metadata.toString()));
    }
  }

  /**
   * @deprecated Use {@link #log(AuditAction, Long, Long, String)} instead.
   */
  @Deprecated
  public void log(String action, Long tenantId, Long userId, String details) {
    try {
      AuditAction a = AuditAction.valueOf(action);
      log(a, tenantId, userId, details);
    } catch (IllegalArgumentException e) {
      log.warn("Legacy log called with non-enum value: {}. Logging as string.", action);
      AuditLog auditEntry = Objects.requireNonNull(
          AuditLog.builder()
              .tenantId(tenantId)
              .userId(userId)
              .action(action)
              .details(details)
              .build());
      auditLogRepository.save(auditEntry);
    }
  }

  public void logAudit(
      AuditAction action, AuditResource resource, Long resourceId, String details) {
    Long tenantId = null;
    Long userId = null;

    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails detailsObj) {
      tenantId = detailsObj.getTenantId();
      userId = detailsObj.getUserId();
    }

    // Fallback for tenantId if not in auth (e.g., during creation flows)
    if (tenantId == null) {
      tenantId = TenantContext.getTenantId();
    }

    String fullDetails = String.format(
        "[%s:%s] %s", resource.name(), resourceId != null ? resourceId : "N/A", details);
    log(action, tenantId != null ? tenantId : TenantContext.PLATFORM_TENANT_ID,
        userId != null ? userId : TenantContext.PLATFORM_TENANT_ID, Objects.requireNonNull(fullDetails));
  }

  /**
   * @deprecated Use {@link #logAudit(AuditAction, AuditResource, Long, String)}
   *             instead.
   */
  @Deprecated
  public void logAudit(String action, String resource, Long resourceId, String details) {
    try {
      AuditAction a = AuditAction.valueOf(action);
      AuditResource r = AuditResource.valueOf(resource);
      logAudit(a, r, resourceId, details);
    } catch (IllegalArgumentException e) {
      log.warn(
          "Legacy audit log called with non-enum values: action={}, resource={}. Logging as string.",
          action,
          resource);
      // Fallback if enums don't match yet
      Long tenantId = TenantContext.getTenantId();
      log.info("LEGACY-AUDIT: tenantId={} action={} details={}", tenantId, action, details);
    }
  }

  public Page<AuditLogResponse> getAllLogs(
      Pageable pageable) {
    var logs = auditLogRepository.findAll(pageable);

    if (isSystemAdmin() && systemConfigService.isSupportModeEnabled()) {
      return logs.map(this::toDto);
    }
    return logs.map(this::toMaskedDto);
  }

  public Page<AuditLogResponse> getTenantLogs(Pageable pageable) {
    TenantContext.assertTenantPresent();
    var logs = auditLogRepository.findAll(pageable);

    if (isSystemAdmin() && systemConfigService.isSupportModeEnabled()) {
      return logs.map(this::toDto);
    }
    return logs.map(this::toMaskedDto);
  }

  public Page<AuditLogResponse> getLogsForTenant(Long tenantId, Pageable pageable) {
    Long previousTenantId = TenantContext.getTenantId();
    TenantContext.setTenantId(tenantId);
    try {
      var logs = auditLogRepository.findAll(pageable);
      if (isSystemAdmin() && systemConfigService.isSupportModeEnabled()) {
        return logs.map(this::toDto);
      }
      return logs.map(this::toMaskedDto);
    } finally {
      TenantContext.setTenantId(previousTenantId);
    }
  }

  public Page<AuditLogResponse> getTenantLogsAsDto(Pageable pageable) {
    TenantContext.assertTenantPresent();
    var logs = auditLogRepository.findAll(pageable);
    return logs.map(this::toMaskedDto);
  }

  private boolean isSystemAdmin() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
      return "PLATFORM".equals(details.getScope()) && "ROOT".equals(details.getRole());
    }
    return false;
  }

  private AuditLogResponse toDto(AuditLog log) {
    return Objects.requireNonNull(AuditLogResponse.builder()
        .id(Objects.requireNonNull(log.getId()))
        .tenantId(Objects.requireNonNull(log.getTenantId()))
        .userId(Objects.requireNonNull(log.getUserId()))
        .action(Objects.requireNonNull(log.getAction()))
        .details(Objects.requireNonNull(log.getDetails()))
        .createdAt(Objects.requireNonNull(log.getCreatedAt()))
        .build());
  }

  private AuditLogResponse toMaskedDto(AuditLog log) {
    return Objects.requireNonNull(AuditLogResponse.builder()
        .id(Objects.requireNonNull(log.getId()))
        .tenantId(Objects.requireNonNull(log.getTenantId()))
        .userId(Objects.requireNonNull(log.getUserId()))
        .action(Objects.requireNonNull(log.getAction()))
        .details(Objects.requireNonNull(maskDetails(Objects.requireNonNull(log.getDetails()))))
        .createdAt(Objects.requireNonNull(log.getCreatedAt()))
        .build());
  }

  private String maskDetails(String details) {
    // Mask sensitive tokens/passwords while preserving audit context
    return Objects.requireNonNull(details.replaceAll("(?i)(password|token|secret|key)=[^,\\]\\s]+", "$1=****"));
  }
}
