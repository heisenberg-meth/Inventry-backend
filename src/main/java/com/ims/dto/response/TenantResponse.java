package com.ims.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {
  private Long id;
  private String name;
  private String domain;

  @JsonProperty("business_type")
  private String businessType;

  private String plan;
  private String status;

  @JsonProperty("created_at")
  private LocalDateTime createdAt;
}
