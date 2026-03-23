package com.ims.tenant.service;

import com.ims.model.Invoice;
import com.ims.model.Order;
import com.ims.model.OrderItem;
import com.ims.model.Product;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public Invoice createFromOrder(Order order, Long tenantId) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seq = 1;
        try {
            seq = invoiceRepository.findMaxSequenceByTenantId(tenantId) + 1;
        } catch (Exception e) {
            // first invoice
        }

        String invoiceNumber = String.format("INV-%d-%s-%04d", tenantId, dateStr, seq);

        Invoice invoice = Invoice.builder()
                .tenantId(tenantId)
                .orderId(order.getId())
                .invoiceNumber(invoiceNumber)
                .amount(order.getTotalAmount())
                .taxAmount(order.getTaxAmount())
                .status("UNPAID")
                .dueDate(LocalDate.now().plusDays(30))
                .build();

        invoice = invoiceRepository.save(invoice);
        log.info("Invoice created: {} for order {}", invoiceNumber, order.getId());
        return invoice;
    }

    public byte[] generatePdf(Long invoiceId, Long tenantId) {
        Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

        List<OrderItem> items = orderItemRepository.findByOrderId(invoice.getOrderId());

        // Build PDF using simple formatting (iText usage simplified for compilation)
        StringBuilder sb = new StringBuilder();
        sb.append("INVOICE\n");
        sb.append("=======\n\n");
        sb.append("Business: ").append(tenant.getName()).append("\n");
        sb.append("Invoice #: ").append(invoice.getInvoiceNumber()).append("\n");
        sb.append("Date: ").append(invoice.getCreatedAt()).append("\n");
        sb.append("Due Date: ").append(invoice.getDueDate()).append("\n\n");
        sb.append("Items:\n");
        sb.append(String.format("%-30s %10s %10s %10s %10s\n", "Product", "Qty", "Price", "Discount", "Total"));
        sb.append("-".repeat(80)).append("\n");

        for (OrderItem item : items) {
            Product product = productRepository.findById(item.getProductId()).orElse(null);
            String productName = product != null ? product.getName() : "Unknown";
            sb.append(String.format("%-30s %10d %10s %10s %10s\n",
                    productName, item.getQuantity(), item.getUnitPrice(), item.getDiscount(), item.getTotal()));
        }

        sb.append("-".repeat(80)).append("\n");
        sb.append(String.format("%-62s %10s\n", "Subtotal:", invoice.getAmount()));
        if (invoice.getTaxAmount() != null) {
            sb.append(String.format("%-62s %10s\n", "Tax:", invoice.getTaxAmount()));
            sb.append(String.format("%-62s %10s\n", "Grand Total:", invoice.getAmount().add(invoice.getTaxAmount())));
        }

        // For MVP, return text-based PDF content as bytes
        // Full iText PDF generation can be added in production
        try {
            return generateITextPdf(tenant, invoice, items);
        } catch (Exception e) {
            log.warn("iText PDF generation failed, using text fallback: {}", e.getMessage());
            return sb.toString().getBytes();
        }
    }

    private byte[] generateITextPdf(Tenant tenant, Invoice invoice, List<OrderItem> items) {
        try {
            var baos = new java.io.ByteArrayOutputStream();
            var writer = new com.itextpdf.kernel.pdf.PdfWriter(baos);
            var pdf = new com.itextpdf.kernel.pdf.PdfDocument(writer);
            var document = new com.itextpdf.layout.Document(pdf);

            // Header
            document.add(new com.itextpdf.layout.element.Paragraph(tenant.getName())
                    .setFontSize(20).setBold());
            document.add(new com.itextpdf.layout.element.Paragraph("Invoice: " + invoice.getInvoiceNumber())
                    .setFontSize(14));
            document.add(new com.itextpdf.layout.element.Paragraph("Date: " + invoice.getCreatedAt()));
            document.add(new com.itextpdf.layout.element.Paragraph("Due: " + invoice.getDueDate()));
            document.add(new com.itextpdf.layout.element.Paragraph(" "));

            // Table
            var table = new com.itextpdf.layout.element.Table(5);
            table.addHeaderCell("Product");
            table.addHeaderCell("Qty");
            table.addHeaderCell("Unit Price");
            table.addHeaderCell("Discount");
            table.addHeaderCell("Total");

            for (OrderItem item : items) {
                Product product = productRepository.findById(item.getProductId()).orElse(null);
                table.addCell(product != null ? product.getName() : "—");
                table.addCell(String.valueOf(item.getQuantity()));
                table.addCell(item.getUnitPrice().toString());
                table.addCell(item.getDiscount().toString());
                table.addCell(item.getTotal().toString());
            }

            document.add(table);
            document.add(new com.itextpdf.layout.element.Paragraph(" "));
            document.add(new com.itextpdf.layout.element.Paragraph("Total: " + invoice.getAmount())
                    .setBold().setFontSize(14));
            if (invoice.getTaxAmount() != null) {
                document.add(new com.itextpdf.layout.element.Paragraph("Tax: " + invoice.getTaxAmount()));
                document.add(new com.itextpdf.layout.element.Paragraph("Grand Total: " + invoice.getAmount().add(invoice.getTaxAmount()))
                        .setBold().setFontSize(16));
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    public Page<Invoice> getInvoices(Pageable pageable) {
        Long tenantId = TenantContext.get();
        return invoiceRepository.findByTenantId(tenantId, pageable);
    }

    public Invoice getInvoiceById(Long id) {
        Long tenantId = TenantContext.get();
        return invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));
    }
}
