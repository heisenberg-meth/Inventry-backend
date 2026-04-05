package com.ims.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
  private String accessToken;
  private String refreshToken;
  private long expiresIn;
  private UserResponse user;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UserResponse {
    private String id;
    private String name;
    private String email;
    private String type;
    private String platformRole;
  }
}
