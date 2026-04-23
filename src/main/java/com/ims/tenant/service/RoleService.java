package com.ims.tenant.service;

import com.ims.dto.request.AssignPermissionsRequest;
import com.ims.dto.request.CreateRoleRequest;
import com.ims.model.Permission;
import com.ims.model.Role;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.tenant.repository.PermissionRepository;
import com.ims.tenant.repository.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final AuditLogService auditLogService;

  @Transactional(readOnly = true)
  public List<Role> findByTenant(@NonNull Long tenantId) {
    return roleRepository.findByTenantIdOrderByNameAsc(tenantId);
  }

  @Transactional(readOnly = true)
  public Role findOne(@NonNull Long tenantId, @NonNull Long roleId) {
    return roleRepository
        .findByIdAndTenantId(roleId, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Role not found"));
  }

  @Transactional
  public Role create(@NonNull Long tenantId, @NonNull CreateRoleRequest request) {
    if (roleRepository.findByNameAndTenantId(request.getName(), tenantId).isPresent()) {
      throw new IllegalArgumentException("Role already exists: " + request.getName());
    }

    Role role =
        Role.builder()
            .name(request.getName())
            .description(request.getDescription())
            .build();

    Role saved = roleRepository.save(Objects.requireNonNull(role));
    auditLogService.log(
        AuditAction.CREATE_ROLE, tenantId, null, "Created role: " + saved.getName());
    log.info("Role created: id={} name={}", saved.getId(), saved.getName());
    return saved;
  }

  @Transactional
  @CacheEvict(value = "permissions", allEntries = true, cacheResolver = "tenantAwareCacheResolver")
  public Role assignPermissions(
      @NonNull Long tenantId,
      @NonNull Long roleId,
      @NonNull AssignPermissionsRequest request) {

    Role role =
        roleRepository
            .findByIdAndTenantId(roleId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Role not found"));

    List<Permission> permissions =
        permissionRepository.findByIdIn(request.getPermissionIds());

    if (permissions.size() != request.getPermissionIds().size()) {
      throw new IllegalArgumentException("Some permission IDs are invalid");
    }

    role.setPermissions(permissions);
    Role saved = roleRepository.save(role);

    auditLogService.log(
        AuditAction.ASSIGN_PERMISSIONS,
        tenantId,
        null,
        "Assigned " + permissions.size() + " permissions to role: " + role.getName());

    return saved;
  }
}
