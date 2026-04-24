package com.ims.tenant.domain.pharmacy;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExpiryAlertService {

  private static final int EXPIRY_THRESHOLD_DAYS = 30;

  private final PharmacyProductRepository pharmacyProductRepository;
  private final com.ims.platform.repository.TenantRepository tenantRepository;

  @Scheduled(cron = "0 0 8 * * *")
  public void checkExpiryAlerts() {
    com.ims.shared.auth.TenantContext.runWithTenant(
        com.ims.shared.auth.TenantContext.SYSTEM_TENANT_ID,
        () -> {
          log.info("Scheduled Task: Checking pharmacy expiry alerts across all tenants");
          List<Long> tenantIds = tenantRepository.findAllIds();
          int[] totalExpiring = {0};

          for (Long tenantId : tenantIds) {
            com.ims.shared.auth.TenantContext.runWithTenant(
                tenantId,
                () -> {
                  LocalDate threshold = LocalDate.now().plusDays(EXPIRY_THRESHOLD_DAYS);
                  List<PharmacyProduct> expiring =
                      pharmacyProductRepository.findByExpiryDateBefore(threshold);

                  for (PharmacyProduct pp : expiring) {
                    log.warn(
                        "EXPIRY ALERT: tenant={} product={} expires={}",
                        tenantId,
                        pp.getProduct().getName(),
                        pp.getExpiryDate());
                    totalExpiring[0]++;
                  }
                });
          }

          log.info(
              "Expiry check complete. {} products expiring within {} days across all tenants.",
              totalExpiring[0],
              EXPIRY_THRESHOLD_DAYS);
        });
  }
}
