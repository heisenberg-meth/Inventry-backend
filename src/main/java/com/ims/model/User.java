package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String email;

  @Column private String phone;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private String role;

  @Column(nullable = false)
  @Builder.Default
  private String scope = "TENANT";

  @Column(name = "is_platform_user")
  @Builder.Default
  private Boolean isPlatformUser = false;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "user_permissions",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  @Builder.Default
  private Set<Permission> customPermissions = new HashSet<>();

  @Column(name = "is_active")
  @Builder.Default
  private Boolean isActive = true;

  @Column(name = "is_verified")
  @Builder.Default
  private Boolean isVerified = false;

  @Column(name = "reset_token")
  private String resetToken;

  @Column(name = "reset_token_expiry")
  private LocalDateTime resetTokenExpiry;

  @Column(name = "last_login")
  private LocalDateTime lastLogin;
}
