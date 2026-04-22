package com.ims.shared.audit;

import com.ims.platform.service.SystemConfigService;
import com.ims.shared.auth.JwtAuthDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.lang.NonNull;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;
  private final SystemConfigService systemConfigService;

  public void log(AuditAction action, Long tenantId, Long userId, String details) {
    log.info("AUDIT: action={} details={}", action, details);

    com.ims.model.AuditLog auditEntry = com.ims.model.AuditLog.builder()
        .tenantId(tenantId)
        .userId(userId)
        .action(action.name())
        .details(details)
        .build();

    auditLogRepository.save(auditEntry);
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
      com.ims.model.AuditLog auditEntry = com.ims.model.AuditLog.builder()
          .tenantId(tenantId)
          .userId(userId)
          .action(action)
          .details(details)
          .build();
      auditLogRepository.save(auditEntry);
    }
  }

  public void logAudit(AuditAction action, AuditResource resource, Long resourceId, String details) {
    Long tenantId = null;
    Long userId = null;

    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails detailsObj) {
      tenantId = detailsObj.getTenantId();
      userId = detailsObj.getUserId();
    }

    // Fallback for tenantId if not in auth (e.g., during creation flows)
    if (tenantId == null) {
      tenantId = com.ims.shared.auth.TenantContext.getTenantId();
    }

    String fullDetails = String.format("[%s:%s] %s", resource.name(), resourceId != null ? resourceId : "N/A", details);
    log(action, tenantId, userId, fullDetails);
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
      log.warn("Legacy audit log called with non-enum values: action={}, resource={}. Logging as string.", action,
          resource);
      // Fallback if enums don't match yet
      Long tenantId = com.ims.shared.auth.TenantContext.getTenantId();
      log.info("LEGACY-AUDIT: action={} details={}", action, details);
    }
  }

  public org.springframework.data.domain.Page<com.ims.model.AuditLog> getAllLogs(
      @NonNull org.springframework.data.domain.Pageable pageable) {
    var logs = auditLogRepository.findAll(pageable);

    // Unmask for ROOT when support mode is explicitly enabled
    if (isSystemAdmin() && systemConfigService.isSupportModeEnabled()) {
      return logs; // full data visible for support investigation
    }
    return logs.map(this::maskSensitiveData); // everyone else gets masked
  }

  public org.springframework.data.domain.Page<com.ims.model.AuditLog> getTenantLogs(Long tenantId,
      @NonNull org.springframework.data.domain.Pageable pageable) {
    var logs = auditLogRepository.findByTenantId(tenantId, pageable);

    // Unmask for ROOT when support mode is explicitly enabled
    if (isSystemAdmin() && systemConfigService.isSupportModeEnabled()) {
      return logs;
    }
    return logs.map(this::maskSensitiveData);
  }

  private boolean isSystemAdmin() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
      return "PLATFORM".equals(details.getScope()) && "ROOT".equals(details.getRole());
    }
    return false;
  }

  private com.ims.model.AuditLog maskSensitiveData(com.ims.model.AuditLog log) {
    if (log.getDetails() != null) {
      log.setDetails("[MASKED - SUPPORT_MODE DISABLED]");
    }
    return log;
  }
}
