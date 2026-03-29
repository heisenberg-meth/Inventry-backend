package com.ims.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Update tenant-specific settings")
public class UpdateTenantSettingsRequest {

  @Schema(description = "Business name of the tenant")
  private String name;

  @Schema(description = "Domain name for the tenant's instance")
  private String domain;

  @Schema(description = "Starting value or sequence for invoices")
  private Integer invoiceSequence;
}
