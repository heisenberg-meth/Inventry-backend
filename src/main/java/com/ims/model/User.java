package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

  public static class UserBuilder {
    private Long id;
    private Long version;
    private Long tenantId;
    private String name;
    private String email;
    private String phone;
    private String passwordHash;
    private Role roleValue;
    private boolean roleSet = false;
    private String scope;
    private Boolean isPlatformUser;
    private Set<Permission> customPermissions;
    private Boolean isActive;
    private Boolean isVerified;
    private String resetToken;
    private LocalDateTime resetTokenExpiry;
    private String verificationToken;
    private LocalDateTime verificationTokenExpiry;
    private LocalDateTime lastLogin;
    private boolean twoFactorEnabled;
    private String twoFactorSecret;
    private String backupCodes;
    private LocalDateTime createdAt;

    public UserBuilder role(Role role) {
      if (this.roleSet) {
        throw new IllegalStateException(
            "Role already set to: " + (this.roleValue != null ? this.roleValue.getName() : "null"));
      }
      this.roleValue = role;
      this.roleSet = true;
      return this;
    }

    public UserBuilder role(UserRole roleEnum) {
      return this.role(Role.builder().name(Objects.requireNonNull(roleEnum.name())).build());
    }

    public User build() {
      Role finalRole = roleSet ? roleValue : null;
      return new User(id, version, tenantId, name, email, phone, passwordHash, finalRole,
          scope != null ? scope : "TENANT",
          isPlatformUser != null ? isPlatformUser : false,
          customPermissions != null ? customPermissions : new HashSet<>(),
          isActive != null ? isActive : true,
          isVerified != null ? isVerified : false,
          resetToken, resetTokenExpiry, verificationToken, verificationTokenExpiry, lastLogin,
          twoFactorEnabled, twoFactorSecret, backupCodes,
          createdAt != null ? createdAt : LocalDateTime.now());
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  private Long version;

  @TenantId
  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Column
  private String phone;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  /**
   * Proper relational mapping for the user's role.
   * Fetch type is LAZY to avoid over-fetching in list contexts.
   */
  @ManyToOne(fetch = FetchType.LAZY)
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
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "permission_id"))
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

  @Column(name = "verification_token")
  private String verificationToken;

  @Column(name = "verification_token_expiry")
  private LocalDateTime verificationTokenExpiry;

  @Column(name = "last_login")
  private LocalDateTime lastLogin;

  @Column(name = "two_factor_enabled")
  @Builder.Default
  private boolean twoFactorEnabled = false;

  @Column(name = "two_factor_secret")
  private String twoFactorSecret;

  @Column(name = "backup_codes", columnDefinition = "TEXT")
  private String backupCodes;

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
