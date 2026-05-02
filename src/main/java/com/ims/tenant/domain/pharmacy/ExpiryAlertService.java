package com.ims.tenant.domain.pharmacy;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.TenantContext;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!test")
public class ExpiryAlertService {

  private static final int EXPIRY_THRESHOLD_DAYS = 30;

  private final PharmacyProductRepository pharmacyProductRepository;
  private final TenantRepository tenantRepository;

  @Scheduled(cron = "0 0 8 * * *")
  public void checkExpiryAlerts() {
    TenantContext.runWithTenant(
        TenantContext.PLATFORM_TENANT_ID,
        () -> {
          log.info("Scheduled Task: Checking pharmacy expiry alerts across all tenants");
          List<Long> tenantIds = tenantRepository.findAllIds();
          int[] totalExpiring = { 0 };

          for (Long tenantId : tenantIds) {
            com.ims.shared.auth.TenantContext.runWithTenant(
                Objects.requireNonNull(tenantId),
                () -> {
                  LocalDate threshold = LocalDate.now().plusDays(EXPIRY_THRESHOLD_DAYS);
                  List<PharmacyProduct> expiring = pharmacyProductRepository.findByExpiryDateBefore(threshold);

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
