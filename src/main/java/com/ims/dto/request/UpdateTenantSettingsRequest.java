package com.ims.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
  @Size(max = 255, message = "Name must not exceed 255 characters")
  private String name;

  @Schema(description = "Workspace slug for the tenant's instance")
  @Size(max = 100, message = "Workspace slug must not exceed 100 characters")
  private String workspaceSlug;

  @Schema(description = "Starting value or sequence for invoices")
  @Positive(message = "Invoice sequence must be positive")
  private Integer invoiceSequence;

  @Schema(description = "Threshold in days for expiry alerts")
  @Positive(message = "Expiry threshold must be positive")
  private Integer expiryThresholdDays;

  @Schema(description = "Business address")
  @Size(max = 500, message = "Address must not exceed 500 characters")
  private String address;

  @Schema(description = "GSTIN of the business")
  @Size(max = 15, message = "GSTIN must not exceed 15 characters")
  private String gstin;
}
