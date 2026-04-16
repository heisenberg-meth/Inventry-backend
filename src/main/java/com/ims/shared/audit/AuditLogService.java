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
@SuppressWarnings("null")
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;
  private final SystemConfigService systemConfigService;

  public void log(String action, Long tenantId, Long userId, String details) {
    log.info("AUDIT: tenant={} user={} action={} details={}", tenantId, userId, action, details);
    
    com.ims.model.AuditLog auditEntry = com.ims.model.AuditLog.builder()
        .tenantId(tenantId)
        .userId(userId)
        .action(action)
        .details(details)
        .build();
    
    auditLogRepository.save(auditEntry);
  }

  public void logAudit(String action, String resource, Long resourceId, String details) {
    Long tenantId = null;
    Long userId = null;

    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails detailsObj) {
      tenantId = detailsObj.getTenantId();
      userId = detailsObj.getUserId();
    }

    String fullDetails = String.format("[%s:%s] %s", resource, resourceId != null ? resourceId : "N/A", details);
    log(action, tenantId, userId, fullDetails);
  }

  public org.springframework.data.domain.Page<com.ims.model.AuditLog> getAllLogs(@NonNull org.springframework.data.domain.Pageable pageable) {
    var logs = auditLogRepository.findAll(pageable);
    
    // Unmask for ROOT when support mode is explicitly enabled
    if (isSystemAdmin() && systemConfigService.isSupportModeEnabled()) {
        return logs; // full data visible for support investigation
    }
    return logs.map(this::maskSensitiveData); // everyone else gets masked
  }

  public org.springframework.data.domain.Page<com.ims.model.AuditLog> getTenantLogs(Long tenantId, @NonNull org.springframework.data.domain.Pageable pageable) {
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
