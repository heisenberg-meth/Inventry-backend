package com.ims.tenant.repository;

import com.ims.model.User;
import com.ims.model.UserRole;

import jakarta.transaction.Transactional;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    /**
     * Optimized detail fetch with JOIN FETCH for roles and permissions.
     * Prevents N+1 queries when accessing these relationships in the detail view.
     */
    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.role r
            LEFT JOIN FETCH r.permissions
            LEFT JOIN FETCH u.customPermissions
            WHERE u.id = :id
            """)
    Optional<User> findByIdWithFullDetails(@Param("id") Long id);

    /**
     * Legacy method maintained for compatibility during migration.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.customPermissions WHERE u.id = :id")
    Optional<User> findByIdWithPermissions(@Param("id") Long id);

    @Query(value = "SELECT u.* FROM users u WHERE u.email = :email", nativeQuery = true)
    Optional<User> findByEmailGlobal(@Param("email") String email);

    @Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
    Optional<User> findByIdGlobal(@Param("id") Long id);

    @Query(value = "SELECT r.name FROM users u JOIN roles r ON u.role_id = r.id WHERE u.id = :userId", nativeQuery = true)
    Optional<String> findRoleNameByUserId(@Param("userId") Long userId);

    // findById is inherited

    @Query(value = "SELECT * FROM users WHERE id = :id AND tenant_id IS NULL", nativeQuery = true)
    Optional<User> findByIdAndTenantIdIsNull(@Param("id") Long id);

    @Query(value = "SELECT * FROM users WHERE tenant_id IS NULL", nativeQuery = true)
    Page<User> findByTenantIdIsNull(Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    long countActive();

    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    long countActiveGlobal();

    @Query(value = "SELECT COUNT(*) FROM users WHERE tenant_id = :tenantId AND is_active = true", nativeQuery = true)
    long countActiveByTenantId(@Param("tenantId") Long tenantId);

    boolean existsByEmail(String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email")
    boolean existsByEmailGlobal(@Param("email") String email);

    @Query(value = "SELECT * FROM users WHERE reset_token = :token AND tenant_id = :tenantId", nativeQuery = true)
    Optional<User> findByResetToken(@Param("token") String token, @Param("tenantId") Long tenantId);

    /**
     * Optimized user summary listing using interface projection.
     * Resolves roles in a single JOIN query.
     */
    @Query("""
            SELECT u.id as id, u.name as name, u.email as email,
                   r.name as roleName, u.scope as scope,
                   u.isActive as isActive, u.createdAt as createdAt
            FROM User u
            LEFT JOIN u.role r
            WHERE u.scope = 'TENANT'
            """)
    Page<UserSummaryView> findSummaries(Pageable pageable);

    @Query(value = "SELECT u.* FROM users u JOIN roles r ON u.role_id = r.id WHERE u.tenant_id = :tenantId AND r.name = :role LIMIT 1", nativeQuery = true)
    Optional<User> findFirstByTenantIdAndRole(
            @Param("tenantId") Long tenantId, @Param("role") String role);

    default Optional<User> findFirstByTenantIdAndAdminRole(Long tenantId) {
        return findFirstByTenantIdAndRole(tenantId, UserRole.TENANT_ADMIN.name());
    }

    @Query(value = "SELECT * FROM users WHERE tenant_id = :tenantId AND scope = 'TENANT'", nativeQuery = true)
    Page<User> findByTenantIdAndScope(@Param("tenantId") Long tenantId, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u "
            + "WHERE u.tenant_id = :tenantId AND u.scope = 'TENANT'"
            + " AND (u.name ILIKE '%' || :search || '%' OR u.email ILIKE '%' || :search || '%')", nativeQuery = true)
    Page<User> findByTenantIdAndSearch(
            @Param("tenantId") Long tenantId, @Param("search") String search, Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.lastLogin = :lastLogin WHERE u.id = :id")
    void updateLastLogin(@Param("id") Long id, @Param("lastLogin") LocalDateTime lastLogin);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM users WHERE tenant_id = :tenantId)", nativeQuery = true)
    boolean existsByTenantId(@Param("tenantId") Long tenantId);

    List<User> findByResetTokenIsNotNullAndResetTokenExpiryBefore(
            LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE User u
            SET u.resetToken = null,
                u.resetTokenExpiry = null
            WHERE u.resetTokenExpiry < :now
            """)
    int clearExpiredResetTokens(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE User u
            SET u.resetToken = null,
                u.resetTokenExpiry = null
            WHERE u.resetTokenExpiry < :now
            """)
    int clearAllExpiredResetTokens(@Param("now") LocalDateTime now);
}
