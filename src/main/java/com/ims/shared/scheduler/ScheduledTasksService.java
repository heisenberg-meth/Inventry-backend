package com.ims.shared.scheduler;

import com.ims.model.Alert;
import com.ims.model.Invoice;
import com.ims.model.Tenant;
import com.ims.product.Product;
import com.ims.product.ProductRepository;
import com.ims.platform.repository.TenantRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.UserRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.notification.AlertRepository;
import com.ims.shared.notification.NotificationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Profile("!test")
public class ScheduledTasksService {

  private final TenantRepository tenantRepository;
  private final ProductRepository productRepository;
  private final InvoiceRepository invoiceRepository;
  private final AlertRepository alertRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService;

  // Every 6 hours
  @Scheduled(cron = "0 0 */6 * * *")
  @Transactional
  public void checkLowStock() {
    TenantContext.runWithTenant(TenantContext.SYSTEM_TENANT_ID, () -> {
      log.info("Scheduled Task: Checking low stock across all tenants");
      List<Long> tenantIds = tenantRepository.findAllIds();

      for (Long tenantId : tenantIds) {
        TenantContext.runWithTenant(tenantId, () -> {
          List<Product> lowStock = productRepository.findLowStock(tenantId);
          
          for (Product p : lowStock) {
            if (alertRepository.findByTypeAndResourceIdAndIsDismissedFalse("LOW_STOCK", p.getId()).isEmpty()) {
              Alert alert = Alert.builder()
                  .tenantId(tenantId)
                  .type("LOW_STOCK")
                  .severity("HIGH")
                  .message("Low stock for " + p.getName() + " (" + p.getStock() + " remaining)")
                  .resourceId(p.getId())
                  .build();
              alertRepository.save(alert);
              
                userRepository.findFirstByTenantIdAndRole(tenantId, "ADMIN").ifPresent(admin -> {
                  notificationService.createNotification(tenantId, admin.getId(), "Low Stock Alert", alert.getMessage(), "LOW_STOCK", p.getId());
              });
            }
          }
        });
      }
    });
  }

  // Daily at midnight
  @Scheduled(cron = "0 0 0 * * *")
  @Transactional
  public void checkOverdueInvoices() {
    TenantContext.runWithTenant(TenantContext.SYSTEM_TENANT_ID, () -> {
      log.info("Scheduled Task: Checking overdue invoices");
      List<Long> tenantIds = tenantRepository.findAllIds();

      for (Long tenantId : tenantIds) {
        TenantContext.runWithTenant(tenantId, () -> {
          var overdue = invoiceRepository.findByStatusNotAndDueDateBefore("PAID", LocalDate.now(), org.springframework.data.domain.Pageable.unpaged());
          
          for (Invoice inv : overdue.getContent()) {
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
          }
        });
      }
    });
  }

  // Daily at 1 AM
  @Scheduled(cron = "0 0 1 * * *")
  @Transactional
  public void cleanupTokens() {
    TenantContext.runWithTenant(TenantContext.SYSTEM_TENANT_ID, () -> {
      log.info("Scheduled Task: Cleaning up expired reset tokens across all tenants");
      List<Long> tenantIds = tenantRepository.findAllIds();
      int count[] = {0};

      for (Long tenantId : tenantIds) {
        TenantContext.runWithTenant(tenantId, () -> {
          int updated = userRepository.clearExpiredResetTokens(tenantId, LocalDateTime.now());
          count[0] += updated;
        });
      }
      log.info("Cleaned up {} expired reset tokens", count[0]);
    });
  }

  // Every 30 minutes
  @Scheduled(fixedRate = 1800000)
  @Transactional
  public void cleanupOrphanTenants() {
    TenantContext.runWithTenant(TenantContext.SYSTEM_TENANT_ID, () -> {
      log.info("Scheduled Task: Cleaning up orphan tenants (no users after 1 hour)");
      LocalDateTime threshold = LocalDateTime.now().minusHours(1);
      List<Tenant> potentiallyOrphaned = tenantRepository.findAllByCreatedAtBefore(threshold);

      int count = 0;
      for (Tenant tenant : potentiallyOrphaned) {
        if (!hasUsers(tenant.getId())) {
            log.warn("Deleting orphan tenant: {} (ID: {})", tenant.getName(), tenant.getId());
            tenantRepository.delete(tenant);
            count++;
        }
      }
      if (count > 0) {
        log.info("Cleaned up {} orphan tenants", count);
      }
    });
  }

  private boolean hasUsers(Long tenantId) {
      final boolean[] result = {false};
      TenantContext.runWithTenant(tenantId, () -> {
          result[0] = userRepository.existsByTenantId(tenantId);
      });
      return result[0];
  }
}
