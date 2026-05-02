package com.ims.shared.exception;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Builder
public class ApiError {
    private final String message;
    private final String code;
    private final int status;
    private final LocalDateTime timestamp;
    private final String path;
    @JsonProperty("correlation_id")
    private final String correlationId;
    private final Map<String, String> errors;
}
