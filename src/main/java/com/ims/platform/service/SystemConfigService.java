package com.ims.platform.service;

import com.ims.model.SystemConfig;
import com.ims.platform.repository.SystemConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

  private final SystemConfigRepository systemConfigRepository;

  public List<SystemConfig> getAllConfigs() {
    return systemConfigRepository.findAll();
  }

  @Cacheable(value = "systemConfig", key = "#key")
  public String getConfig(String key, String defaultValue) {
    return systemConfigRepository
        .findById(Objects.requireNonNull(key))
        .map(SystemConfig::getValue)
        .orElse(defaultValue);
  }

  @Transactional
  @CacheEvict(value = "systemConfig", key = "#key")
  public SystemConfig updateConfig(String key, String value) {
    SystemConfig config = systemConfigRepository
        .findById(Objects.requireNonNull(key))
        .orElseThrow(() -> new EntityNotFoundException("Config not found: " + key));
    config.setValue(value);
    config.setUpdatedAt(Objects.requireNonNull(LocalDateTime.now()));
    log.info("System config updated: {} = {}", key, value);
    return systemConfigRepository.save(config);
  }

  public boolean isPharmacyEnabled() {
    return "true".equalsIgnoreCase(getConfig("PHARMACY_EXTENSION_ENABLED", "true"));
  }

  public boolean isSupportModeEnabled() {
    return "true".equalsIgnoreCase(getConfig("SUPPORT_MODE", "false"));
  }
}
