package com.ims.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
  @Nullable private Long id;
  private String name;
  private String email;
  @Nullable private String role;
  @Nullable private String scope;

  @Nullable private List<String> permissions;

  @JsonProperty("is_active")
  @Nullable private Boolean isActive;

  @JsonProperty("created_at")
  @Nullable private LocalDateTime createdAt;
}
