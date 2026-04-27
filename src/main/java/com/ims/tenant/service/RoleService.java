package com.ims.tenant.service;

import com.ims.dto.request.AssignPermissionsRequest;
import com.ims.dto.request.CreateRoleRequest;
import com.ims.model.Permission;
import com.ims.model.Role;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.audit.AuditResource;
import com.ims.tenant.repository.PermissionRepository;
import com.ims.tenant.repository.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  public List<Role> findAll() {
    return Objects.requireNonNull(roleRepository.findAllByOrderByNameAsc());
  }

  @Transactional(readOnly = true)
  public Role findOne(Long roleId) {
    return Objects.requireNonNull(
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new EntityNotFoundException("Role not found")));
  }

  @Transactional
  public Role create(CreateRoleRequest request) {
    if (roleRepository.findByName(request.getName()).isPresent()) {
      throw new IllegalArgumentException("Role already exists: " + request.getName());
    }

    Role role = Role.builder().name(request.getName()).description(request.getDescription()).build();

    Role saved = roleRepository.save(Objects.requireNonNull(role));
    auditLogService.logAudit(
        AuditAction.CREATE_ROLE, AuditResource.ROLE, saved.getId(), "Created role: " + saved.getName());
    log.info("Role created: id={} name={}", saved.getId(), saved.getName());
    return saved;
  }

  @Transactional
  @CacheEvict(value = "permissions", allEntries = true, cacheResolver = "tenantAwareCacheResolver")
  public Role assignPermissions(
      Long roleId, AssignPermissionsRequest request) {

    Role role = roleRepository
        .findById(roleId)
        .orElseThrow(() -> new EntityNotFoundException("Role not found"));

    List<Permission> permissions = permissionRepository.findByIdIn(request.getPermissionIds());

    if (permissions.size() != request.getPermissionIds().size()) {
      throw new IllegalArgumentException("Some permission IDs are invalid");
    }

    role.setPermissions(permissions);
    Role saved = roleRepository.save(role);

    auditLogService.logAudit(
        AuditAction.ASSIGN_PERMISSIONS,
        AuditResource.ROLE,
        role.getId(),
        "Assigned " + permissions.size() + " permissions to role: " + role.getName());

    return saved;
  }
}
