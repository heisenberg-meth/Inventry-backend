package com.ims.shared.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.model.AuditLog;
import com.ims.shared.auth.TenantContext;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PreRemove;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuditEntityListener {

  private static AuditLogRepository staticRepository;

  @Autowired
  public void setRepository(AuditLogRepository repository) {
    AuditEntityListener.staticRepository = repository;
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @PostPersist
  public void onPostPersist(Object entity) {
    logAudit(AuditAction.CREATE, entity, null, toMap(entity));
  }

  @PostUpdate
  public void onPostUpdate(Object entity) {
    logAudit(AuditAction.UPDATE, entity, null, toMap(entity));
  }

  @PreRemove
  public void onPreRemove(Object entity) {
    logAudit(AuditAction.DELETE, entity, toMap(entity), null);
  }

  private void logAudit(AuditAction action, Object entity, Map<String, Object> oldValue,
      Map<String, Object> newValue) {
    if (staticRepository == null) {
      log.warn("Audit repository not initialized, skipping audit log");
      return;
    }

    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      return;
    }

    String entityType = entity.getClass().getSimpleName();
    Long entityId = extractEntityId(entity);

    AuditLog auditEntry = AuditLog.builder()
        .tenantId(tenantId)
        .action(action.name())
        .entityType(entityType)
        .entityId(entityId)
        .oldValue(serialize(oldValue))
        .newValue(serialize(newValue))
        .build();

    try {
      staticRepository.save(auditEntry);
    } catch (Exception e) {
      log.error("Failed to save audit log for {}:{} action={}", entityType, entityId, action, e);
    }
  }

  private Long extractEntityId(Object entity) {
    try {
      var method = entity.getClass().getMethod("getId");
      Object result = method.invoke(entity);
      return result instanceof Long ? (Long) result : null;
    } catch (Exception e) {
      return null;
    }
  }

  private Map<String, Object> toMap(Object entity) {
    try {
      return MAPPER.convertValue(entity, new TypeReference<Map<String, Object>>() {
      });
    } catch (Exception e) {
      log.warn("Failed to serialize entity to map: {}", entity.getClass().getSimpleName(), e);
      return Map.of();
    }
  }

  private String serialize(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return null;
    }
    try {
      Map<String, Object> sanitized = new HashMap<>(map);
      sanitized.remove("passwordHash");
      sanitized.remove("version");
      return MAPPER.writeValueAsString(sanitized);
    } catch (Exception e) {
      log.warn("Failed to serialize audit value", e);
      return null;
    }
  }
}
