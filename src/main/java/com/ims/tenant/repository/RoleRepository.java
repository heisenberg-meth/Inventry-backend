package com.ims.tenant.repository;

import com.ims.model.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

  @Query("SELECT r FROM Role r WHERE r.tenantId = :tenantId ORDER BY r.name ASC")
  List<Role> findAllByTenantId(@Param("tenantId") Long tenantId);

  @Query("SELECT r FROM Role r WHERE r.name = :name AND r.tenantId = :tenantId")
  Optional<Role> findByNameAndTenantId(@Param("name") String name, @Param("tenantId") Long tenantId);

  Optional<Role> findByNameAndTenantIdIsNull(String name);

  boolean existsByTenantId(Long tenantId);

  @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.name = :name AND r.tenantId = :tenantId")
  Optional<Role> findByNameWithPermissions(@Param("name") String name, @Param("tenantId") Long tenantId);

  @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.name = :name AND r.tenantId IS NULL")
  Optional<Role> findByNameAndTenantIdIsNullWithPermissions(@Param("name") String name);
}
