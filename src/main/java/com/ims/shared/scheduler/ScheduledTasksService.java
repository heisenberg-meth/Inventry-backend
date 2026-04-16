package com.ims.shared.scheduler;

import com.ims.model.Alert;
import com.ims.model.Invoice;
import com.ims.model.Tenant;
import com.ims.model.User;
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
@SuppressWarnings("null")
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
    log.info("Scheduled Task: Checking low stock across all tenants");
    List<Tenant> tenants = tenantRepository.findAll();

    for (Tenant tenant : tenants) {
      try {
        TenantContext.set(tenant.getId());
        List<Product> lowStock = productRepository.findLowStock();
        
        for (Product p : lowStock) {
          // Create or update alert
          if (alertRepository.findByTypeAndResourceIdAndIsDismissedFalse("LOW_STOCK", p.getId()).isEmpty()) {
            Alert alert = Alert.builder()
                .tenantId(tenant.getId())
                .type("LOW_STOCK")
                .severity("HIGH")
                .message("Low stock for " + p.getName() + " (" + p.getStock() + " remaining)")
                .resourceId(p.getId())
                .build();
            alertRepository.save(alert);
            
            // Notify tenant admin if possible
            userRepository.findFirstByTenantIdAndRole(tenant.getId(), "ADMIN").ifPresent(admin -> {
                notificationService.createNotification(admin.getId(), "Low Stock Alert", alert.getMessage(), "LOW_STOCK", p.getId());
            });
          }
        }
      } finally {
        TenantContext.clear();
      }
    }
  }

  // Daily at midnight
  @Scheduled(cron = "0 0 0 * * *")
  @Transactional
  public void checkOverdueInvoices() {
    log.info("Scheduled Task: Checking overdue invoices");
    List<Tenant> tenants = tenantRepository.findAll();

    for (Tenant tenant : tenants) {
      try {
        TenantContext.set(tenant.getId());
        // Simple unpaged check for all overdue
        var overdue = invoiceRepository.findByStatusNotAndDueDateBefore("PAID", LocalDate.now(), org.springframework.data.domain.Pageable.unpaged());
        
        for (Invoice inv : overdue.getContent()) {
          if (alertRepository.findByTypeAndResourceIdAndIsDismissedFalse("OVERDUE_INVOICE", inv.getId()).isEmpty()) {
            Alert alert = Alert.builder()
                .tenantId(tenant.getId())
                .type("OVERDUE_INVOICE")
                .severity("MEDIUM")
                .message("Invoice " + inv.getInvoiceNumber() + " is overdue since " + inv.getDueDate())
                .resourceId(inv.getId())
                .build();
            alertRepository.save(alert);
          }
        }
      } finally {
        TenantContext.clear();
      }
    }
  }

  // Daily at 1 AM
  @Scheduled(cron = "0 0 1 * * *")
  @Transactional
  public void cleanupTokens() {
    log.info("Scheduled Task: Cleaning up expired reset tokens");
    // Find users with expired reset tokens
    List<User> users = userRepository.findAll(); // Unfiltered by default usually
    int count = 0;
    for (User user : users) {
      if (user.getResetToken() != null && user.getResetTokenExpiry() != null && 
          LocalDateTime.now().isAfter(user.getResetTokenExpiry())) {
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        count++;
      }
    }
    log.info("Cleaned up {} expired reset tokens", count);
  }
}
