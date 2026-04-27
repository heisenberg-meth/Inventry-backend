package com.ims.tenant.service;

import com.ims.dto.CreateInvoiceRequest;
import com.ims.dto.InvoiceStatusRequest;
import com.ims.model.Customer;
import com.ims.model.Invoice;
import com.ims.model.InvoiceStatus;
import com.ims.model.Order;
import com.ims.model.OrderItem;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.product.Product;
import com.ims.product.ProductRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.pdf.PdfService;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
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
import org.springframework.lang.Nullable;
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

  /**
   * Length of the "INV-" prefix that we strip when deriving credit-note numbers.
   */
  private static final int INVOICE_PREFIX_LENGTH = 4;

  @Transactional
  public Invoice createManual(CreateInvoiceRequest request) {
    Order order = orderRepository
        .findById(Objects.requireNonNull(request.getOrderId()))
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if (!"SALE".equals(order.getType())) {
      throw new IllegalArgumentException("Invoice can only be created for SALE orders");
    }

    Long tenantId = TenantContext.requireTenantId();
    if (invoiceRepository.existsByOrderId(order.getId(), tenantId)) {
      throw new IllegalArgumentException("Invoice already exists for this order");
    }

    String invoiceNumber = incrementAndGetInvoiceNumber();

    Invoice invoice = Objects.requireNonNull(
        Invoice.builder()
            .orderId(Objects.requireNonNull(order.getId()))
            .invoiceNumber(invoiceNumber)
            .amount(order.getTotalAmount())
            .taxAmount(order.getTaxAmount())
            .discount(order.getDiscount())
            .status(InvoiceStatus.UNPAID)
            .dueDate(
                Objects.requireNonNull(
                    request.getDueDate() != null
                        ? request.getDueDate()
                        : LocalDate.now().plusDays(DEFAULT_DUE_DAYS)))
            .build());

    log.info("Manual invoice created: {} for order {}", invoiceNumber, order.getId());
    return Objects.requireNonNull(invoiceRepository.save(invoice));
  }

  @Transactional
  public Invoice updateStatus(Long id, InvoiceStatusRequest request) {
    Invoice invoice = invoiceRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

    InvoiceStatus currentStatus = invoice.getStatus();
    InvoiceStatus newStatus = request.getStatus();

    if (InvoiceStatus.PAID.equals(currentStatus) || InvoiceStatus.CANCELLED.equals(currentStatus)) {
      throw new IllegalArgumentException("Cannot update status from " + currentStatus);
    }

    invoice.setStatus(Objects.requireNonNull(newStatus));
    if (InvoiceStatus.PAID.equals(newStatus)) {
      invoice.setPaidAt(
          Objects.requireNonNull(request.getPaidAt() != null ? request.getPaidAt() : LocalDateTime.now()));
    }

    return invoiceRepository.save(invoice);
  }

  @Transactional
  public Invoice createFromOrder(Order order) {
    String invoiceNumber = incrementAndGetInvoiceNumber();

    Invoice invoice = Objects.requireNonNull(
        Invoice.builder()
            .orderId(Objects.requireNonNull(order.getId()))
            .invoiceNumber(invoiceNumber)
            .amount(order.getTotalAmount())
            .taxAmount(order.getTaxAmount())
            .discount(order.getDiscount())
            .status(InvoiceStatus.UNPAID)
            .dueDate(Objects.requireNonNull(LocalDate.now().plusDays(DEFAULT_DUE_DAYS)))
            .build());

    log.info("Invoice created: {} for order {}", invoiceNumber, order.getId());
    return Objects.requireNonNull(invoiceRepository.save(invoice));
  }

  @Transactional
  public Invoice createCreditNote(Order returnOrder, @Nullable Long parentInvoiceId) {
    // Use CN prefix in place of the invoice's "INV-" prefix.
    String invoiceNumber = "CN-" + incrementAndGetInvoiceNumber().substring(INVOICE_PREFIX_LENGTH);

    Invoice creditNote = Objects.requireNonNull(
        Invoice.builder()
            .orderId(Objects.requireNonNull(returnOrder.getId()))
            .invoiceNumber(invoiceNumber)
            .amount(Objects.requireNonNull(returnOrder.getTotalAmount().negate()))
            .taxAmount(
                Objects.requireNonNull(
                    returnOrder.getTaxAmount() != null
                        ? returnOrder.getTaxAmount().negate()
                        : BigDecimal.ZERO))
            .discount(
                Objects.requireNonNull(
                    returnOrder.getDiscount() != null
                        ? returnOrder.getDiscount().negate()
                        : BigDecimal.ZERO))
            .status(InvoiceStatus.PAID) // Credit notes are usually considered "settled" immediately as a reduction
            .parentInvoiceId(parentInvoiceId)
            .dueDate(Objects.requireNonNull(LocalDate.now()))
            .paidAt(Objects.requireNonNull(LocalDateTime.now()))
            .build());

    log.info("Credit note created: {} for return order {}", invoiceNumber, returnOrder.getId());
    return Objects.requireNonNull(invoiceRepository.save(creditNote));
  }

  private String incrementAndGetInvoiceNumber() {
    Long tenantId = TenantContext.requireTenantId();
    
    // PESSIMISTIC LOCK: Ensure no other thread can increment the sequence for this tenant simultaneously
    Tenant tenant = tenantRepository.lockById(tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
    
    int newSequence = tenant.getInvoiceSequence() + 1;
    tenant.setInvoiceSequence(newSequence);
    tenantRepository.saveAndFlush(tenant);

    String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    return Objects.requireNonNull(String.format("INV-%d-%s-%04d", tenantId, dateStr, newSequence));
  }

  @Transactional(readOnly = true)
  public byte[] generatePdf(Long id) {
    Invoice invoice = invoiceRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

    Order order = orderRepository
        .findById(Objects.requireNonNull(invoice.getOrderId()))
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    Tenant tenant = tenantRepository
        .findById(Objects.requireNonNull(TenantContext.getTenantId()))
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    Customer customer = customerRepository
        .findById(Objects.requireNonNull(order.getCustomerId(), "customer id required"))
        .orElseThrow(() -> new EntityNotFoundException("Customer not found"));

    List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

    List<Map<String, Object>> items = orderItems.stream()
        .map(
            item -> {
              Product product = productRepository
                  .findById(
                      Objects.requireNonNull(item.getProductId(), "product id required"))
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
    context.setVariable(
        "tenantAddress", tenant.getAddress() != null ? tenant.getAddress() : "Company Address TBD");
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

    return Objects.requireNonNull(pdfService.generatePdfFromHtml("invoice-template", context));
  }

  public Page<Invoice> getInvoices(Pageable pageable) {
    Long tenantId = TenantContext.requireTenantId();
    return Objects.requireNonNull(invoiceRepository.findByTenantIdAndIsActiveTrue(tenantId, pageable));
  }

  public Page<Invoice> getOverdueInvoices(Pageable pageable) {
    Long tenantId = TenantContext.requireTenantId();
    return Objects.requireNonNull(
        invoiceRepository.findByTenantIdAndStatusNotAndDueDateBefore(tenantId, InvoiceStatus.PAID, LocalDate.now(), pageable));
  }

  public Invoice getInvoiceById(Long id) {
    Long tenantId = TenantContext.requireTenantId();
    return Objects.requireNonNull(
        invoiceRepository
            .findById(id) // Standard findById needs care if not isolated by DB policy
            .filter(inv -> tenantId.equals(inv.getTenantId()))
            .orElseThrow(() -> new EntityNotFoundException("Invoice not found")));
  }
}
