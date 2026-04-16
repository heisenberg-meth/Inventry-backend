package com.ims.tenant.service;

import com.ims.dto.CreateInvoiceRequest;
import com.ims.dto.InvoiceStatusRequest;
import com.ims.model.Customer;
import com.ims.model.Invoice;
import com.ims.model.Order;
import com.ims.model.OrderItem;
import com.ims.model.Product;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.pdf.PdfService;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

  private final InvoiceRepository invoiceRepository;
  private final OrderItemRepository orderItemRepository;
  private final ProductRepository productRepository;
  private final TenantRepository tenantRepository;
  private final OrderRepository orderRepository;
  private final CustomerRepository customerRepository;
  private final PdfService pdfService;

  private static final int DEFAULT_DUE_DAYS = 30;

  @Transactional
  public @NonNull Invoice createManual(@NonNull CreateInvoiceRequest request) {
    Order order =
        orderRepository
            .findById(Objects.requireNonNull(request.getOrderId()))
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if (!"SALE".equals(order.getType())) {
      throw new IllegalArgumentException("Invoice can only be created for SALE orders");
    }

    if (invoiceRepository.existsByOrderId(order.getId())) {
      throw new IllegalArgumentException("Invoice already exists for this order");
    }

    String invoiceNumber = incrementAndGetInvoiceNumber();

    Invoice invoice =
        Invoice.builder()
            .orderId(order.getId())
            .invoiceNumber(invoiceNumber)
            .amount(order.getTotalAmount())
            .taxAmount(order.getTaxAmount())
            .discount(order.getDiscount())
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
  public @NonNull Invoice updateStatus(@NonNull Long id, @NonNull InvoiceStatusRequest request) {
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
  public @NonNull Invoice createFromOrder(@NonNull Order order) {
    String invoiceNumber = incrementAndGetInvoiceNumber();

    Invoice invoice =
        Invoice.builder()
            .orderId(order.getId())
            .invoiceNumber(invoiceNumber)
            .amount(order.getTotalAmount())
            .taxAmount(order.getTaxAmount())
            .discount(order.getDiscount())
            .status("UNPAID")
            .dueDate(LocalDate.now().plusDays(DEFAULT_DUE_DAYS))
            .build();

    log.info("Invoice created: {} for order {}", invoiceNumber, order.getId());
    return invoiceRepository.save(invoice);
  }

  private String incrementAndGetInvoiceNumber() {
    Tenant tenant =
        tenantRepository
            .findById(Objects.requireNonNull(TenantContext.get()))
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    tenant.setInvoiceSequence(tenant.getInvoiceSequence() + 1);
    tenantRepository.save(tenant);

    String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    return String.format("INV-%d-%s-%04d", tenant.getId(), dateStr, tenant.getInvoiceSequence());
  }

  @Transactional(readOnly = true)
  public byte[] generatePdf(@NonNull Long id) {
    Invoice invoice =
        invoiceRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

    Order order =
        orderRepository
            .findById(Objects.requireNonNull(invoice.getOrderId()))
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    Tenant tenant =
        tenantRepository
            .findById(Objects.requireNonNull(TenantContext.get()))
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    Customer customer =
        customerRepository
            .findById(Objects.requireNonNull(order.getCustomerId()))
            .orElseThrow(() -> new EntityNotFoundException("Customer not found"));

    List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

    List<Map<String, Object>> items =
        orderItems.stream()
            .map(
                item -> {
                  Product product =
                      productRepository
                          .findById(Objects.requireNonNull(item.getProductId()))
                          .orElseThrow(() -> new EntityNotFoundException("Product not found"));
                  Map<String, Object> map = new HashMap<>();
                  map.put("productName", product.getName());
                  map.put("quantity", item.getQuantity());
                  map.put("unitPrice", item.getUnitPrice());
                  map.put("discount", item.getDiscount());
                  map.put("total", item.getTotal());
                  return map;
                })
            .collect(Collectors.toList());

    Context context = new Context();
    context.setVariable("tenantName", tenant.getName());
    context.setVariable("tenantAddress", tenant.getAddress() != null ? tenant.getAddress() : "Company Address TBD");
    context.setVariable("tenantGstin", tenant.getGstin() != null ? tenant.getGstin() : "GSTIN-TBD");

    context.setVariable("customerName", customer.getName());
    context.setVariable("customerAddress", customer.getAddress());
    context.setVariable("customerGstin", customer.getGstin());

    context.setVariable("invoiceNumber", invoice.getInvoiceNumber());
    context.setVariable(
        "invoiceDate",
        invoice.getCreatedAt() != null ? invoice.getCreatedAt().toLocalDate() : LocalDate.now());
    context.setVariable("orderId", order.getId());
    context.setVariable("status", invoice.getStatus());

    context.setVariable("items", items);
    context.setVariable(
        "subtotal", order.getTotalAmount().subtract(order.getTaxAmount()).add(order.getDiscount()));
    context.setVariable("taxAmount", order.getTaxAmount());
    context.setVariable("discount", order.getDiscount());
    context.setVariable("totalAmount", order.getTotalAmount());

    return pdfService.generatePdfFromHtml("invoice-template", context);
  }

  public @NonNull Page<Invoice> getInvoices(@NonNull Pageable pageable) {
    return invoiceRepository.findAll(pageable);
  }

  public @NonNull Invoice getInvoiceById(@NonNull Long id) {
    return invoiceRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));
  }
}
