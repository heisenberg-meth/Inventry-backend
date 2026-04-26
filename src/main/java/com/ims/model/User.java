package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;
import org.springframework.lang.Nullable;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

  public static class UserBuilder {
    public UserBuilder role(Role role) {
      this.role = role;
      return this;
    }

    public UserBuilder role(UserRole roleEnum) {
      this.role = Role.builder().name(Objects.requireNonNull(roleEnum.name())).build();
      return this;
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @org.springframework.lang.Nullable
  private Long id;

  @Version
  @org.springframework.lang.Nullable
  private Long version;

  @TenantId
  @Column(name = "tenant_id")
  @org.springframework.lang.Nullable
  private Long tenantId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Column @org.springframework.lang.Nullable private String phone;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  /**
   * Proper relational mapping for the user's role.
   * Fetch type is LAZY to avoid over-fetching in list contexts.
   */
  @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
  @JoinColumn(name = "role_id")
  private Role role;

  @Column(nullable = false)
  @Builder.Default
  private String scope = "TENANT";

  @Column(name = "is_platform_user")
  @Builder.Default
  private Boolean isPlatformUser = false;

  /**
   * Custom permissions assigned directly to the user.
   * Fetch type is LAZY to prevent N+1 queries during list fetching.
   */
  @ManyToMany(fetch = jakarta.persistence.FetchType.LAZY)
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
  @Nullable
  private String resetToken;

  @Column(name = "reset_token_expiry")
  @Nullable
  private LocalDateTime resetTokenExpiry;

  @Column(name = "verification_token")
  @Nullable
  private String verificationToken;

  @Column(name = "verification_token_expiry")
  @Nullable
  private LocalDateTime verificationTokenExpiry;

  @Column(name = "last_login")
  @Nullable
  private LocalDateTime lastLogin;

  @Column(name = "created_at")
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  /**
   * Utility to check if user has a specific role by name.
   */
  public boolean hasRole(UserRole roleName) {
    return role != null && role.getName().equals(roleName.name());
  }

  /**
   * Utility to check if user has a specific role by string.
   */
  public boolean hasRole(String roleName) {
    return role != null && role.getName().equals(roleName);
  }
}
