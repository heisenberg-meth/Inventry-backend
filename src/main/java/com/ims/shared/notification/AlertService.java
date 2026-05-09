package com.ims.shared.notification;

import com.ims.model.Alert;
import com.ims.model.Inventory;
import com.ims.model.Notification;
import com.ims.product.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;
    private final NotificationRepository notificationRepository;
    private final com.ims.shared.outbox.OutboxService outboxService;
    private final com.ims.shared.metrics.BusinessMetricsService businessMetricsService;

    public void checkLowStock(Product product) {
        if (product.getStock() <= product.getReorderLevel()) {
            String severity = product.getStock() == 0 ? "CRITICAL" : "WARNING";
            String message = String.format("Stock for product %s (%s) is %s. Level: %d, Reorder at: %d",
                    product.getName(), product.getSku(), severity, product.getStock(), product.getReorderLevel());

            log.warn("Low stock alert triggered: {}", message);

            Alert alert = Alert.builder()
                    .tenantId(product.getTenantId())
                    .type("LOW_STOCK")
                    .severity(severity)
                    .message(message)
                    .resourceId(product.getId())
                    .createdAt(LocalDateTime.now())
                    .isDismissed(false)
                    .build();
            Alert saved = alertRepository.save(alert);

            businessMetricsService.incrementLowStockAlerts();

            // Fire outbox event for async processing (e.g. sending SMS/Email via Kafka)
            outboxService.saveEvent("INVENTORY", product.getId().toString(), "LOW_STOCK_ALERT", saved);
        }
    }

    public void checkLowStock(Inventory inventory) {
        if (inventory.isLowStock() || inventory.isBelowReorderLevel()) {
            String severity = inventory.getQuantity() == 0 ? "CRITICAL" : "WARNING";
            String message = String.format(
                    "Inventory for product ID %d is %s. Quantity: %d, Low threshold: %d, Reorder at: %d",
                    inventory.getProductId(), severity, inventory.getQuantity(),
                    inventory.getLowStockThreshold() != null ? inventory.getLowStockThreshold() : 0,
                    inventory.getReorderLevel() != null ? inventory.getReorderLevel() : 0);

            log.warn("Low stock alert triggered: {}", message);

            Alert alert = Alert.builder()
                    .tenantId(inventory.getTenantId())
                    .type("LOW_STOCK")
                    .severity(severity)
                    .message(message)
                    .resourceId(inventory.getProductId())
                    .createdAt(LocalDateTime.now())
                    .isDismissed(false)
                    .build();
            Alert saved = alertRepository.save(alert);

            businessMetricsService.incrementLowStockAlerts();

            outboxService.saveEvent("INVENTORY", inventory.getProductId().toString(), "LOW_STOCK_ALERT", saved);
        }
    }

    @Transactional
    public void createNotification(Long tenantId, Long userId, String title, String message, String type,
            Long resourceId) {
        Notification notification = Notification.builder()
                .tenantId(tenantId)
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .resourceId(resourceId)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notification);
    }
}
