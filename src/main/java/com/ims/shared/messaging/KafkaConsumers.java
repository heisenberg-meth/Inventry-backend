package com.ims.shared.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.model.Alert;
import com.ims.model.Invoice;
import com.ims.model.Order;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.notification.NotificationService;
import com.ims.tenant.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumers {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final InvoiceService invoiceService;

    @KafkaListener(topics = "ims.inventory.low_stock_alert", groupId = "ims-group")
    public void consumeLowStockAlert(String message) {
        try {
            Alert alert = objectMapper.readValue(message, Alert.class);
            log.info("Consumed low stock alert: {}", alert.getMessage());

            TenantContext.setTenantId(alert.getTenantId());
            try {
                // In a real app, this could send an email or push notification
                notificationService.createNotification(
                        null, // All admins/relevant users in tenant
                        "Low Stock Alert",
                        alert.getMessage(),
                        "LOW_STOCK",
                        alert.getResourceId());
            } finally {
                TenantContext.clear();
            }
        } catch (Exception e) {
            log.error("Error processing low stock alert message", e);
        }
    }

    @KafkaListener(topics = "ims.invoice.generate", groupId = "ims-group")
    public void consumeInvoiceGenerate(String message) {
        try {
            Invoice invoice = objectMapper.readValue(message, Invoice.class);
            log.info("Consumed invoice generate: {}", invoice.getInvoiceNumber());

            TenantContext.setTenantId(invoice.getTenantId());
            try {
                // Async PDF generation simulation
                byte[] pdf = invoiceService.generatePdf(invoice.getId());
                log.info("PDF generated for invoice {}: {} bytes", invoice.getInvoiceNumber(), pdf.length);

                // In a real app, upload to S3/Cloud Storage here
            } finally {
                TenantContext.clear();
            }
        } catch (Exception e) {
            log.error("Error processing invoice generate message", e);
        }
    }

    @KafkaListener(topics = "ims.order.created", groupId = "ims-group")
    public void consumeOrderCreated(String message) {
        try {
            Order order = objectMapper.readValue(message, Order.class);
            log.info("Consumed order created: #{}", order.getId());

            // Example: send confirmation email
        } catch (Exception e) {
            log.error("Error processing order created message", e);
        }
    }
}
