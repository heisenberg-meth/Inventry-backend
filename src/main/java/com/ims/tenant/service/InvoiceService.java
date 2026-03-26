package com.ims.tenant.service;

import com.ims.dto.CreateInvoiceRequest;
import com.ims.dto.InvoiceStatusRequest;
import com.ims.model.Invoice;
import com.ims.model.Order;
import com.ims.model.OrderItem;
import com.ims.model.Product;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

  private final InvoiceRepository invoiceRepository;
  private final OrderItemRepository orderItemRepository;
  private final ProductRepository productRepository;
  private final TenantRepository tenantRepository;
  private final OrderRepository orderRepository;

  private static final int DEFAULT_DUE_DAYS = 30;
  private static final int PDF_LINE_WIDTH = 80;
  private static final int PDF_HEADER_FONT_SIZE = 20;
  private static final int PDF_SUBHEADER_FONT_SIZE = 14;
  private static final int PDF_GRAND_TOTAL_FONT_SIZE = 16;
  private static final int PDF_TABLE_COLUMNS = 5;

  @Transactional
  public Invoice createManual(CreateInvoiceRequest request) {
    Order order =
        orderRepository
            .findById(request.getOrderId())
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if (!"SALE".equals(order.getType())) {
      throw new IllegalArgumentException("Invoice can only be created for SALE orders");
    }

    if (invoiceRepository.existsByOrderId(order.getId())) {
      throw new IllegalArgumentException("Invoice already exists for this order");
    }

    Tenant tenant =
        tenantRepository
            .findById(TenantContext.get())
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    tenant.setInvoiceSequence(tenant.getInvoiceSequence() + 1);
    tenantRepository.save(tenant);

    String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String invoiceNumber =
        String.format("INV-%d-%s-%04d", TenantContext.get(), dateStr, tenant.getInvoiceSequence());

    Invoice invoice =
        Invoice.builder()
            .orderId(order.getId())
            .invoiceNumber(invoiceNumber)
            .amount(order.getTotalAmount())
            .taxAmount(order.getTaxAmount())
            .status("UNPAID")
            .dueDate(
                request.getDueDate() != null
                    ? request.getDueDate()
                    : LocalDate.now().plusDays(DEFAULT_DUE_DAYS))
            .build();

    log.info("Manual invoice created: {} for order {}", invoiceNumber, order.getId());
    return invoiceRepository.save(invoice);
  }

  @Transactional
  public Invoice updateStatus(Long id, InvoiceStatusRequest request) {
    Invoice invoice =
        invoiceRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

    String currentStatus = invoice.getStatus();
    String newStatus = request.getStatus();

    if ("PAID".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
      throw new IllegalArgumentException("Cannot update status from " + currentStatus);
    }
    if (!"PAID".equals(newStatus)
        && !"PARTIAL".equals(newStatus)
        && !"CANCELLED".equals(newStatus)) {
      throw new IllegalArgumentException("Invalid status: " + newStatus);
    }

    invoice.setStatus(newStatus);
    if ("PAID".equals(newStatus)) {
      invoice.setPaidAt(request.getPaidAt() != null ? request.getPaidAt() : LocalDateTime.now());
    }

    return invoiceRepository.save(invoice);
  }

  @Transactional
  public Invoice createFromOrder(Order order) {
    String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    int seq = 1;
    try {
      seq = invoiceRepository.findMaxSequence() + 1;
    } catch (Exception e) {
      log.trace("Caught expected exception for first invoice sequence: {}", e.getMessage());
    }

    String invoiceNumber = String.format("INV-%d-%s-%04d", TenantContext.get(), dateStr, seq);

    Invoice invoice =
        Invoice.builder()
            .orderId(order.getId())
            .invoiceNumber(invoiceNumber)
            .amount(order.getTotalAmount())
            .taxAmount(order.getTaxAmount())
            .status("UNPAID")
            .dueDate(LocalDate.now().plusDays(DEFAULT_DUE_DAYS))
            .build();

    invoice = invoiceRepository.save(invoice);
    log.info("Invoice created: {} for order {}", invoiceNumber, order.getId());
    return invoice;
  }

  public byte[] generatePdf(Long invoiceId) {
    Tenant tenant =
        tenantRepository
            .findById(TenantContext.get())
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    // Build PDF using simple formatting (iText usage simplified for compilation)
    StringBuilder sb = new StringBuilder();
    sb.append("INVOICE\n");
    sb.append("=======\n\n");
    sb.append("Business: ").append(tenant.getName()).append("\n");

    Invoice invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));
    sb.append("Invoice #: ").append(invoice.getInvoiceNumber()).append("\n");
    sb.append("Date: ").append(invoice.getCreatedAt()).append("\n");
    sb.append("Due Date: ").append(invoice.getDueDate()).append("\n\n");
    sb.append("Items:\n");
    sb.append(
        String.format(
            "%-30s %10s %10s %10s %10s\n", "Product", "Qty", "Price", "Discount", "Total"));
    sb.append("-".repeat(PDF_LINE_WIDTH)).append("\n");

    List<OrderItem> items = orderItemRepository.findByOrderId(invoice.getOrderId());
    for (OrderItem item : items) {
      Product product = productRepository.findById(item.getProductId()).orElse(null);
      String productName = product != null ? product.getName() : "Unknown";
      sb.append(
          String.format(
              "%-30s %10d %10s %10s %10s\n",
              productName,
              item.getQuantity(),
              item.getUnitPrice(),
              item.getDiscount(),
              item.getTotal()));
    }

    sb.append("-".repeat(PDF_LINE_WIDTH)).append("\n");
    sb.append(String.format("%-62s %10s\n", "Subtotal:", invoice.getAmount()));
    if (invoice.getTaxAmount() != null) {
      sb.append(String.format("%-62s %10s\n", "Tax:", invoice.getTaxAmount()));
      sb.append(
          String.format(
              "%-62s %10s\n", "Grand Total:", invoice.getAmount().add(invoice.getTaxAmount())));
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
      document.add(
          new com.itextpdf.layout.element.Paragraph(tenant.getName())
              .setFontSize(PDF_HEADER_FONT_SIZE)
              .setBold());
      document.add(
          new com.itextpdf.layout.element.Paragraph("Invoice: " + invoice.getInvoiceNumber())
              .setFontSize(PDF_SUBHEADER_FONT_SIZE));
      document.add(new com.itextpdf.layout.element.Paragraph("Date: " + invoice.getCreatedAt()));
      document.add(new com.itextpdf.layout.element.Paragraph("Due: " + invoice.getDueDate()));
      document.add(new com.itextpdf.layout.element.Paragraph(" "));

      // Table
      var table = new com.itextpdf.layout.element.Table(PDF_TABLE_COLUMNS);
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
      document.add(
          new com.itextpdf.layout.element.Paragraph("Total: " + invoice.getAmount())
              .setBold()
              .setFontSize(PDF_SUBHEADER_FONT_SIZE));
      if (invoice.getTaxAmount() != null) {
        document.add(new com.itextpdf.layout.element.Paragraph("Tax: " + invoice.getTaxAmount()));
        document.add(
            new com.itextpdf.layout.element.Paragraph(
                    "Grand Total: " + invoice.getAmount().add(invoice.getTaxAmount()))
                .setBold()
                .setFontSize(PDF_GRAND_TOTAL_FONT_SIZE));
      }

      document.close();
      return baos.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("PDF generation failed", e);
    }
  }

  public Page<Invoice> getInvoices(Pageable pageable) {
    return invoiceRepository.findAll(pageable);
  }

  public Invoice getInvoiceById(Long id) {
    return invoiceRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));
  }
}
