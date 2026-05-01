package com.ims.shared.auth;

import com.ims.model.Permission;
import com.ims.tenant.repository.PermissionRepository;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionCacheService {

  private final PermissionRepository permissionRepository;
  private Map<String, Permission> permissionCache = Collections.emptyMap();

  private static final Set<String> REQUIRED_PERMISSION_KEYS = Set.of(
      "manage_platform",
      "view_business", "create_business",
      "view_user", "create_user", "update_user", "delete_user",
      "view_product", "create_product", "update_product", "delete_product",
      "stock_in", "stock_out",
      "view_reports", "create_invoice",
      "create_category", "update_category", "delete_category",
      "create_supplier", "update_supplier", "delete_supplier");

  @PostConstruct
  public void init() {
    refreshCache();
    validateRequiredPermissions();
  }

  public void refreshCache() {
    List<Permission> all = permissionRepository.findAll();
    this.permissionCache = all.stream()
        .collect(Collectors.toUnmodifiableMap(Permission::getKey, Function.identity()));
    log.info("Loaded {} permissions into cache", permissionCache.size());
  }

  public void validateRequiredPermissions() {
    Set<String> missing = REQUIRED_PERMISSION_KEYS.stream()
        .filter(key -> !permissionCache.containsKey(key))
        .collect(Collectors.toSet());

    if (!missing.isEmpty()) {
      log.error("CRITICAL: Missing required permissions in database: {}", missing);
      // FR-03-G: Fail fast if required permissions are missing
      throw new IllegalStateException("Missing required permissions: " + missing);
    }
    log.info("Permission integrity validated. All {} required permissions present.", REQUIRED_PERMISSION_KEYS.size());
  }

  public Permission getByKey(String key) {
    Permission p = permissionCache.get(key);
    if (p == null) {
      throw new IllegalArgumentException("Unknown permission key: " + key);
    }
    return p;
  }

  public List<Permission> getByKeys(Collection<String> keys) {
    return keys.stream()
        .map(this::getByKey)
        .collect(Collectors.toList());
  }

  public Map<String, Permission> getAll() {
    return permissionCache;
  }
}
