package com.ims.tenant.domain.pharmacy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExpiryAlertService {

    private final PharmacyProductRepository pharmacyProductRepository;

    @Scheduled(cron = "0 0 8 * * *")
    public void checkExpiryAlerts() {
        LocalDate threshold = LocalDate.now().plusDays(30);
        List<PharmacyProduct> expiring = pharmacyProductRepository.findByExpiryDateBefore(threshold);

        expiring.forEach(pp -> log.warn(
                "EXPIRY ALERT: tenant={} product={} expires={}",
                pp.getProduct().getTenantId(),
                pp.getProduct().getName(),
                pp.getExpiryDate()));

        log.info("Expiry check complete. {} products expiring within 30 days.", expiring.size());
    }
}
