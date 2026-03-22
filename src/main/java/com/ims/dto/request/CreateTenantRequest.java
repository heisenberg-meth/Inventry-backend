package com.ims.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTenantRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String domain;

    @NotBlank(message = "Business type is required")
    private String businessType;

    private String plan;
}
