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

  List<Role> findAllByOrderByNameAsc();

  Optional<Role> findByName(String name);

  Optional<Role> findByNameAndTenantId(String name, Long tenantId);

  boolean existsByTenantId(Long tenantId);

  @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.name = :name")
  Optional<Role> findByNameWithPermissions(@Param("name") String name);

  @Query(value = "SELECT * FROM roles WHERE name = :name AND tenant_id IS NULL", nativeQuery = true)
  Optional<Role> findByNameAndTenantIdIsNull(@Param("name") String name);

  @Query(value = "SELECT r.* FROM roles r WHERE r.name = :name AND r.tenant_id IS NULL", nativeQuery = true)
  Optional<Role> findByNameAndTenantIdIsNullWithPermissions(@Param("name") String name);
}
