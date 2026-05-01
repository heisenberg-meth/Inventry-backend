package com.ims.shared.auth;

import com.ims.category.CategoryService;
import com.ims.dto.CategoryRequest;
import com.ims.model.Role;
import com.ims.model.User;
import com.ims.model.UserRole;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.tenant.repository.PermissionRepository;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.UserRepository;
import java.util.List;
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
  private final PermissionRepository permissionRepository;
  private final CategoryService categoryService;
  private final AuditLogService auditLogService;

  @Transactional(propagation = Propagation.REQUIRED)
  public User initializeTenant(
      User user, Long tenantId, String tenantName) {
    Long oldTenantId = TenantContext.getTenantId();
    try {
      TenantContext.setTenantId(tenantId);

      // 1. Seed default roles
      seedDefaultRoles(tenantId);

      // 2. Resolve TENANT_ADMIN role for the owner
      Role adminRole = roleRepository.findByName(UserRole.TENANT_ADMIN.name())
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
    List<com.ims.model.Permission> allPermissions = permissionRepository.findAll();

    UserRole[] rolesToSeed = new UserRole[] {
        UserRole.TENANT_ADMIN,
        UserRole.BUSINESS_MANAGER,
        UserRole.SALES_STAFF,
        UserRole.INVENTORY_MANAGER,
        UserRole.FINANCE_MANAGER,
        UserRole.VIEWER
    };

    for (UserRole roleName : rolesToSeed) {
      if (roleRepository.findByNameAndTenantId(roleName.name(), tenantId).isEmpty()) {
        Role role = Role.builder()
            .name(Objects.requireNonNull(roleName.name()))
            .description("Default " + roleName.name() + " role")
            .tenantId(tenantId)
            .build();

        // Attach permissions immediately (PRD §3.2 Step 2)
        // Critical rule: Do NOT create roles without permissions
        role.setPermissions(allPermissions);

        roleRepository.save(role);
      }
    }
    log.info("Default roles seeded for tenant: {}", tenantId);
  }
}
