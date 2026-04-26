package com.ims.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaRequest {
    @NotBlank(message = "MFA token is required")
    private String mfaToken;
    
    @NotBlank(message = "Verification code is required")
    private String code;
}
