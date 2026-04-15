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
  private TenantResponse tenant;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UserResponse {
    private String id;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String scope;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TenantResponse {
    private Long id;
    private String name;
    private String type;
    private String address;
    private String gstin;
    private String plan;
    private String companyCode;
    private String workspaceSlug;
  }
}
