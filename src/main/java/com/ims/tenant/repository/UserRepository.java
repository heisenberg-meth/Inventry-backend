package com.ims.tenant.repository;

import com.ims.model.User;
import com.ims.model.UserRole;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);

  @Query("SELECT u FROM User u LEFT JOIN FETCH u.customPermissions WHERE u.id = :id")
  Optional<User> findByIdWithPermissions(@Param("id") Long id);

  @Query(value = "SELECT * FROM users WHERE email = :email", nativeQuery = true)
  Optional<User> findByEmailUnfiltered(@Param("email") String email);

  @Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
  Optional<User> findByIdUnfiltered(@Param("id") Long id);

  // findById is inherited

  Optional<User> findByIdAndTenantIdIsNull(Long id);

  Page<User> findByTenantIdIsNull(Pageable pageable);

  @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
  long countActive();

  @Query(value = "SELECT COUNT(*) FROM users WHERE tenant_id = :tenantId AND is_active = true", nativeQuery = true)
  long countActiveByTenantId(@Param("tenantId") Long tenantId);

  boolean existsByEmail(String email);

  @Query(value = "SELECT * FROM users WHERE reset_token = :token", nativeQuery = true)
  Optional<User> findByResetToken(@Param("token") String token);

  @Query(value = "SELECT * FROM users WHERE tenant_id = :tenantId AND role = :role LIMIT 1", nativeQuery = true)
  Optional<User> findFirstByTenantIdAndRole(@Param("tenantId") Long tenantId, @Param("role") String role);
  
  default Optional<User> findFirstByTenantIdAndAdminRole(Long tenantId) {
      return findFirstByTenantIdAndRole(tenantId, UserRole.ADMIN.name());
  }

  @Query(
      value = "SELECT * FROM users WHERE tenant_id = :tenantId AND scope = 'TENANT'",
      nativeQuery = true)
  Page<User> findByTenantIdAndScope(
      @Param("tenantId") Long tenantId, Pageable pageable);

  @Query(
      value =
          "SELECT * FROM users WHERE tenant_id = :tenantId AND scope = 'TENANT'"
              + " AND (name ILIKE '%' || :search || '%' OR email ILIKE '%' || :search || '%')",
      nativeQuery = true)
  Page<User> findByTenantIdAndSearch(
      @Param("tenantId") Long tenantId,
      @Param("search") String search,
      Pageable pageable);
  @org.springframework.data.jpa.repository.Modifying
  @Query("UPDATE User u SET u.lastLogin = :lastLogin WHERE u.id = :id")
  void updateLastLogin(@Param("id") Long id, @Param("lastLogin") java.time.LocalDateTime lastLogin);
  @Query(value = "SELECT EXISTS(SELECT 1 FROM users WHERE tenant_id = :tenantId)", nativeQuery = true)
  boolean existsByTenantId(@Param("tenantId") Long tenantId);

  java.util.List<User> findByResetTokenIsNotNullAndResetTokenExpiryBefore(java.time.LocalDateTime now);

  @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
  @Query("""
      UPDATE User u
      SET u.resetToken = null,
          u.resetTokenExpiry = null
      WHERE u.tenantId = :tenantId AND u.resetTokenExpiry < :now
      """)
  int clearExpiredResetTokens(@Param("tenantId") Long tenantId, @Param("now") java.time.LocalDateTime now);
}
