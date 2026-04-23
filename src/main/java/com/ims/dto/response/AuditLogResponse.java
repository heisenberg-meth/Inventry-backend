package com.ims.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class AuditLogResponse {

    private final Long id;
    private final Long tenantId;
    private final Long userId;
    private final String action;
    private final String details;
    private final LocalDateTime createdAt;
}
