package com.ims.dto.response;

import java.util.Map;

public record ErrorResponse(
        String message,
        Map<String, String> fieldErrors) {
}
