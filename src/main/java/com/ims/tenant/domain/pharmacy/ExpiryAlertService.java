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

  @Scheduled(cron = "0 0 8 * * *")
  public void checkExpiryAlerts() {
    LocalDate threshold = LocalDate.now().plusDays(EXPIRY_THRESHOLD_DAYS);
    List<PharmacyProduct> expiring = pharmacyProductRepository.findByExpiryDateBefore(threshold);

    expiring.forEach(
        pp ->
            log.warn(
                "EXPIRY ALERT: tenant={} product={} expires={}",
                pp.getProduct().getTenantId(),
                pp.getProduct().getName(),
                pp.getExpiryDate()));

    log.info(
        "Expiry check complete. {} products expiring within {} days.",
        expiring.size(),
        EXPIRY_THRESHOLD_DAYS);
  }

}
