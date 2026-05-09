package com.ims.shared.audit;

import com.ims.platform.service.SystemConfigService;
import com.ims.shared.auth.JwtAuthDetails;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;
  private final SystemConfigService systemConfigService;
  private final com.ims.shared.outbox.OutboxService outboxService;
  private final Counter auditWriteCounter;
  private final Counter auditFailureCounter;
  private final Timer auditWriteTimer;

  private static final String CURRENT_REQUEST_ID = "current-request-id";

  public AuditLogService(
      AuditLogRepository auditLogRepository,
      SystemConfigService systemConfigService,
      com.ims.shared.outbox.OutboxService outboxService,
      MeterRegistry meterRegistry) {
    this.auditLogRepository = auditLogRepository;
    this.systemConfigService = systemConfigService;
    this.outboxService = outboxService;
    this.auditWriteCounter = Counter.builder("audit.write.total").register(meterRegistry);
    this.auditFailureCounter = Counter.builder("audit.write.failures").register(meterRegistry);
    this.auditWriteTimer = Timer.builder("audit.write.latency").register(meterRegistry);
  }

  public void log(AuditAction action, Long tenantId, Long userId, String details) {
    log(action, tenantId, userId, null, null, null, null, details);
  }

  public void log(
      AuditAction action,
      Long tenantId,
      Long userId,
      String entityType,
      Long entityId,
      String oldValue,
      String newValue,
      String details) {
    auditWriteTimer.record(
        () -> {
          try {
            String requestId = getCurrentRequestId();
            log.info(
                "AUDIT: tenant={} user={} action={} entity={}:{} request={} details={}",
                tenantId,
                userId,
                action,
                entityType,
                entityId,
                requestId,
                details);

            com.ims.model.AuditLog auditEntry =
                com.ims.model.AuditLog.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .action(action.name())
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .details(details)
                    .requestId(requestId)
                    .build();

            auditEntry =
                Objects.requireNonNull(
                    auditLogRepository.save(auditEntry), "Audit entry must not be null after save");
            auditWriteCounter.increment();

            try {
              outboxService.saveEvent(
                  "AUDIT", auditEntry.getId().toString(), action.name(), auditEntry, tenantId);
            } catch (Exception e) {
              log.warn(
                  "Failed to save audit event to outbox, proceeding anyway: {}", e.getMessage());
            }
          } catch (Exception e) {
            auditFailureCounter.increment();
            log.error(
                "Failed to write audit log (suppressing to prevent business outage): {}",
                e.getMessage(),
                e);
          }
        });
  }

  public void logChange(
      AuditAction action,
      AuditResource resource,
      Long resourceId,
      Object oldState,
      Object newState,
      String details) {
    Long tenantId = com.ims.shared.auth.TenantContext.getTenantId();
    Long userId = null;

    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails detailsObj) {
      userId = detailsObj.getUserId();
    }

    String oldJson = toJson(oldState);
    String newJson = toJson(newState);

    log(action, tenantId, userId, resource.name(), resourceId, oldJson, newJson, details);
  }

  private String toJson(Object obj) {
    if (obj == null) return null;
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
    } catch (Exception e) {
      log.error("Failed to serialize audit state", e);
      return null;
    }
  }

  public static void setCurrentRequestId(String requestId) {
    org.springframework.util.ConcurrentReferenceHashMap<String, String> context =
        getRequestIdContext();
    context.put(CURRENT_REQUEST_ID, requestId);
  }

  private static String getCurrentRequestId() {
    return getRequestIdContext().get(CURRENT_REQUEST_ID);
  }

  private static org.springframework.util.ConcurrentReferenceHashMap<String, String>
      getRequestIdContext() {
    return REQUEST_ID_CONTEXT.get();
  }

  private static final ThreadLocal<
          org.springframework.util.ConcurrentReferenceHashMap<String, String>>
      REQUEST_ID_CONTEXT =
          ThreadLocal.withInitial(
              () -> new org.springframework.util.ConcurrentReferenceHashMap<>(4));

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
      com.ims.model.AuditLog auditEntry =
          com.ims.model.AuditLog.builder()
              .tenantId(tenantId)
              .userId(userId)
              .action(action)
              .details(details)
              .build();
      Objects.requireNonNull(
          auditLogRepository.save(auditEntry), "Audit entry must not be null after save");
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
      tenantId = com.ims.shared.auth.TenantContext.getTenantId();
    }

    log(action, tenantId, userId, resource.name(), resourceId, null, null, details);
  }

  /**
   * @deprecated Use {@link #logAudit(AuditAction, AuditResource, Long, String)} instead.
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
      Long tenantId = com.ims.shared.auth.TenantContext.getTenantId();
      log.info("LEGACY-AUDIT: tenant={} action={} details={}", tenantId, action, details);
    }
  }

  public org.springframework.data.domain.Page<com.ims.model.AuditLog> getAllLogs(
      org.springframework.data.domain.Pageable pageable) {
    var logs = auditLogRepository.findAll(pageable);

    // Unmask for ROOT when support mode is explicitly enabled
    if (isSystemAdmin() && systemConfigService.isSupportModeEnabled()) {
      return logs; // full data visible for support investigation
    }
    return logs.map(this::maskSensitiveData); // everyone else gets masked
  }

  public org.springframework.data.domain.Page<com.ims.model.AuditLog> getTenantLogs(
      org.springframework.data.domain.Pageable pageable) {
    Long tenantId = com.ims.shared.auth.TenantContext.getTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Missing tenant context");
    }
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
