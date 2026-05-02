package com.ims.shared.auth;

import com.ims.category.CategoryService;
import com.ims.dto.CategoryRequest;
import com.ims.model.Role;
import com.ims.model.User;
import com.ims.model.UserRole;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantInitializationService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final CategoryService categoryService;
  private final AuditLogService auditLogService;
  private final PermissionCacheService permissionCacheService;

  @Transactional(propagation = Propagation.REQUIRED)
  public User initializeTenant(
      User user, Long tenantId, String tenantName) {
    Long oldTenantId = TenantContext.getTenantId();
    try {
      TenantContext.setTenantId(tenantId);

      // FR-03-F: Idempotency guard
      if (roleRepository.existsByTenantId(tenantId)) {
        log.warn("Roles already initialized for tenant {}. Skipping seeding.", tenantId);
      } else {
        // 1. Seed default roles
        seedDefaultRoles(tenantId);
      }

      // 2. Resolve TENANT_ADMIN role for the owner
      Role adminRole = roleRepository.findByNameAndTenantId(UserRole.TENANT_ADMIN.name(), tenantId)
          .orElseThrow(() -> new IllegalStateException("TENANT_ADMIN role not seeded for tenant"));
      user.setRole(Objects.requireNonNull(adminRole));

      // 3. Seed default category
      CategoryRequest catReq = new CategoryRequest();
      catReq.setName("General");
      catReq.setDescription("Default category");
      categoryService.create(catReq);

      // Create the owner user
      User savedUser = userRepository.save(user);

      final String finalEmail = savedUser.getEmail();

      // Write audit log INSIDE the transaction (PRD §9.2)
      auditLogService.log(
          AuditAction.USER_CREATED,
          tenantId,
          Objects.requireNonNull(savedUser.getId()),
          "Admin user created for tenant: " + tenantName + " by " + finalEmail);

      return savedUser;
    } finally {
      if (oldTenantId == null) {
        TenantContext.clear();
      } else {
        TenantContext.setTenantId(oldTenantId);
      }
    }
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public User createUserForTenant(User user, Long tenantId) {
    Long oldTenantId = TenantContext.getTenantId();
    try {
      TenantContext.setTenantId(tenantId);
      return userRepository.save(user);
    } finally {
      if (oldTenantId == null) {
        TenantContext.clear();
      } else {
        TenantContext.setTenantId(oldTenantId);
      }
    }
  }

  private void seedDefaultRoles(Long tenantId) {
    // FR-03-C: Bulk lookup from cache
    Map<String, com.ims.model.Permission> allPerms = permissionCacheService.getAll();

    // FR-03-J: Permission Matrix
    seedRole(tenantId, UserRole.TENANT_ADMIN, allPerms.values().stream()
        .filter(p -> !p.getKey().equals("manage_platform"))
        .collect(java.util.stream.Collectors.toList()));

    seedRole(tenantId, UserRole.BUSINESS_MANAGER, filterPerms(allPerms,
        "view_business", "create_business", "_user", "_product", "stock_", "create_invoice", "view_reports",
        "_category", "_supplier"));

    seedRole(tenantId, UserRole.SALES_STAFF, filterPerms(allPerms,
        "view_product", "stock_out", "create_invoice"));

    seedRole(tenantId, UserRole.INVENTORY_MANAGER, filterPerms(allPerms,
        "_product", "stock_", "view_reports", "_category"));

    seedRole(tenantId, UserRole.FINANCE_MANAGER, filterPerms(allPerms,
        "view_reports", "create_invoice", "view_business"));

    seedRole(tenantId, UserRole.VIEWER, allPerms.values().stream()
        .filter(p -> p.getKey().startsWith("view_"))
        .collect(java.util.stream.Collectors.toList()));

    log.info("Default roles seeded for tenant: {}", tenantId);
  }

  private void seedRole(Long tenantId, UserRole roleType, List<com.ims.model.Permission> perms) {
    Role role = Role.builder()
        .name(roleType.name())
        .description("Default " + roleType.name() + " role")
        .tenantId(tenantId)
        .permissions(perms)
        .build();
    roleRepository.save(role);
  }

  private List<com.ims.model.Permission> filterPerms(Map<String, com.ims.model.Permission> all, String... keys) {
    return all.values().stream()
        .filter(p -> {
          for (String k : keys) {
            if (k.startsWith("_")) {
              if (p.getKey().contains(k))
                return true;
            } else {
              if (p.getKey().startsWith(k))
                return true;
            }
          }
          return false;
        })
        .collect(java.util.stream.Collectors.toList());
  }
}