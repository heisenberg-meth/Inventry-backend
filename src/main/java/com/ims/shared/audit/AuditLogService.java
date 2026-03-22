package com.ims.shared.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    public void log(String action, Long tenantId, Long userId, String details) {
        log.info("AUDIT: tenant={} user={} action={} details={}", tenantId, userId, action, details);
    }
}
