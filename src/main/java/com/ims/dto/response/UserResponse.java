package com.ims.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
  private Long id;
  private String name;
  private String email;
  private String role;
  private String scope;

  private List<String> permissions;

  @JsonProperty("is_active")
  private Boolean isActive;

  @JsonProperty("created_at")
  private LocalDateTime createdAt;
}
