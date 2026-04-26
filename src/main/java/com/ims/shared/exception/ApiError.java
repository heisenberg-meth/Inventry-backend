package com.ims.shared.exception;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class ApiError {
    private final String message;
    private final String code;
    private final int status;
    private final LocalDateTime timestamp;
    private final String path;
    private final String traceId;
    private final Map<String, String> errors; // For validation details
}
