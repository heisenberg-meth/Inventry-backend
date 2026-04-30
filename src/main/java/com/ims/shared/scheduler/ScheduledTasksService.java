package com.ims.shared.scheduler;

import com.ims.model.Alert;
import com.ims.model.Invoice;
import com.ims.model.InvoiceStatus;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.product.Product;
import com.ims.product.ProductRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.notification.AlertRepository;
import com.ims.shared.notification.NotificationService;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

@Service
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class ScheduledTasksService {

  private final TenantRepository tenantRepository;
  private final ProductRepository productRepository;
  private final InvoiceRepository invoiceRepository;
  private final AlertRepository alertRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService;

  /**
   * Checks for low stock across ALL tenants in a single pass.
   * Optimized to use streaming to keep memory footprint O(1).
   */
  @Scheduled(cron = "0 0 */6 * * *")
  @SchedulerLock(name = "checkLowStock", lockAtMostFor = "30m", lockAtLeastFor = "5m")
  @Transactional
  public void checkLowStock() {
    log.info("Scheduled Task: Checking low stock across all tenants (Optimized)");

    try (Stream<Product> lowStockStream = productRepository.streamAllLowStockGlobal()) {
      lowStockStream.forEach(this::processLowStockProduct);
    }
  }

  private void processLowStockProduct(Product p) {
    Long tenantId = p.getTenantId();
    TenantContext.runWithTenant(tenantId, () -> {
      if (alertRepository.findByTypeAndResourceIdAndIsDismissedFalse("LOW_STOCK", p.getId()).isEmpty()) {
        Alert alert = Alert.builder()
            .tenantId(tenantId)
            .type("LOW_STOCK")
            .severity("HIGH")
            .message("Low stock for " + p.getName() + " (" + p.getStock() + " remaining)")
            .resourceId(p.getId())
            .build();
        alertRepository.save(alert);

        userRepository.findFirstByTenantIdAndAdminRole(tenantId).ifPresent(admin -> {
          notificationService.createNotification(
              tenantId,
              admin.getId(),
              "Low Stock Alert",
              alert.getMessage(),
              "LOW_STOCK",
              p.getId());
        });
      }
    });
  }

  /**
   * Checks for overdue invoices across ALL tenants in a single pass.
   */
  @Scheduled(cron = "0 0 0 * * *")
  @SchedulerLock(name = "checkOverdueInvoices", lockAtMostFor = "1h", lockAtLeastFor = "10m")
  @Transactional
  public void checkOverdueInvoices() {
    log.info("Scheduled Task: Checking overdue invoices (Optimized)");

    try (Stream<Invoice> overdueStream = invoiceRepository.streamAllOverdueGlobal(
        InvoiceStatus.PAID, Objects.requireNonNull(LocalDate.now()))) {
      overdueStream.forEach(this::processOverdueInvoice);
    }
  }

  private void processOverdueInvoice(Invoice inv) {
    Long tenantId = Objects.requireNonNull(inv.getTenantId());
    TenantContext.runWithTenant(tenantId, () -> {
      if (alertRepository.findByTypeAndResourceIdAndIsDismissedFalse("OVERDUE_INVOICE", inv.getId()).isEmpty()) {
        Alert alert = Alert.builder()
            .tenantId(tenantId)
            .type("OVERDUE_INVOICE")
            .severity("MEDIUM")
            .message("Invoice " + inv.getInvoiceNumber() + " is overdue since " + inv.getDueDate())
            .resourceId(inv.getId())
            .build();
        alertRepository.save(alert);
      }
    });
  }

  /**
   * Bulk cleanup of expired reset tokens across all tenants.
   * Single UPDATE query instead of per-tenant loops.
   */
  @Scheduled(cron = "0 0 1 * * *")
  @SchedulerLock(name = "cleanupTokens", lockAtMostFor = "15m", lockAtLeastFor = "1m")
  @Transactional
  public void cleanupTokens() {
    log.info("Scheduled Task: Cleaning up expired reset tokens (Bulk Optimized)");
    int count = userRepository.clearAllExpiredResetTokens(LocalDateTime.now());
    log.info("Cleaned up {} expired reset tokens in a single operation", count);
  }

  /**
   * Clean up tenants that were created but never had any users.
   */
  @Scheduled(fixedRate = 1800000)
  @SchedulerLock(name = "cleanupOrphanTenants", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  @Transactional
  public void cleanupOrphanTenants() {
    log.info("Scheduled Task: Cleaning up orphan tenants");
    LocalDateTime threshold = LocalDateTime.now().minusHours(1);
    List<Tenant> potentiallyOrphaned = tenantRepository.findAllByCreatedAtBefore(threshold);

    int count = 0;
    for (Tenant tenant : potentiallyOrphaned) {
      if (!userRepository.existsByTenantId(tenant.getId())) {
        log.warn("Deleting orphan tenant: {} (ID: {})", tenant.getName(), tenant.getId());
        tenantRepository.delete(tenant);
        count++;
      }
    }
    if (count > 0) {
      log.info("Cleaned up {} orphan tenants", count);
    }
  }
}
