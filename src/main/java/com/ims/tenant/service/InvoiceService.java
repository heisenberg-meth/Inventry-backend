package com.ims.tenant.service;

import com.ims.dto.CreateInvoiceRequest;
import com.ims.dto.InvoiceStatusRequest;
import com.ims.model.Customer;
import com.ims.model.Invoice;
import com.ims.model.Order;
import com.ims.model.OrderItem;
import com.ims.product.Product;
import com.ims.model.Tenant;
import com.ims.order.entity.OrderType;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.pdf.PdfService;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.product.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

@Slf4j
@Service
public class InvoiceService {

        private final InvoiceRepository invoiceRepository;
        private final OrderItemRepository orderItemRepository;
        private final ProductRepository productRepository;
        private final TenantRepository tenantRepository;
        private final OrderRepository orderRepository;
        private final CustomerRepository customerRepository;
        private final PdfService pdfService;
        private final com.ims.shared.outbox.OutboxService outboxService;

        private final Counter invoiceGeneratedCounter;
        private final Counter invoiceVoidedCounter;
        private final Counter pdfGenerationFailureCounter;
        private final Counter duplicateInvoiceAttemptCounter;
        private final Timer invoiceGenerationTimer;
        private final Timer pdfGenerationTimer;

        private static final int DEFAULT_DUE_DAYS = 30;

        public InvoiceService(
                        InvoiceRepository invoiceRepository,
                        OrderItemRepository orderItemRepository,
                        ProductRepository productRepository,
                        TenantRepository tenantRepository,
                        OrderRepository orderRepository,
                        CustomerRepository customerRepository,
                        PdfService pdfService,
                        com.ims.shared.outbox.OutboxService outboxService,
                        MeterRegistry meterRegistry) {
                this.invoiceRepository = invoiceRepository;
                this.orderItemRepository = orderItemRepository;
                this.productRepository = productRepository;
                this.tenantRepository = tenantRepository;
                this.orderRepository = orderRepository;
                this.customerRepository = customerRepository;
                this.pdfService = pdfService;
                this.outboxService = outboxService;

                this.invoiceGeneratedCounter = Counter.builder("invoice.generated.total").register(meterRegistry);
                this.invoiceVoidedCounter = Counter.builder("invoice.voided.total").register(meterRegistry);
                this.pdfGenerationFailureCounter = Counter.builder("invoice.pdf_generation.failures")
                                .register(meterRegistry);
                this.duplicateInvoiceAttemptCounter = Counter.builder("invoice.duplicate_attempts")
                                .register(meterRegistry);
                this.invoiceGenerationTimer = Timer.builder("invoice.generation.latency").register(meterRegistry);
                this.pdfGenerationTimer = Timer.builder("invoice.pdf_generation.latency").register(meterRegistry);
        }

        @Transactional
        public Invoice createManual(CreateInvoiceRequest request) {
                return invoiceGenerationTimer.record(() -> {
                        Long tenantId = TenantContext.getTenantId();
                        Order order = orderRepository
                                        .findByIdAndTenantId(Objects.requireNonNull(request.getOrderId()), tenantId)
                                        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

                        if (OrderType.SALE != order.getType()) {
                                throw new IllegalArgumentException("Invoice can only be created for SALE orders");
                        }

                        if (invoiceRepository.existsByTenantIdAndOrderId(tenantId, order.getId())) {
                                duplicateInvoiceAttemptCounter.increment();
                                throw new IllegalArgumentException("Invoice already exists for this order");
                        }

                        String invoiceNumber = incrementAndGetInvoiceNumber();

                        Invoice invoice = Invoice.builder()
                                        .tenantId(TenantContext.getTenantId())
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

                        if (invoice.getTenantId() == null) {
                                throw new IllegalStateException("TenantContext missing - cannot create invoice");
                        }

                        log.info("Manual invoice created: {} for order {}", invoiceNumber, order.getId());
                        Invoice saved = invoiceRepository.save(invoice);

                        invoiceGeneratedCounter.increment();
                        outboxService.saveEvent("INVOICE", saved.getId().toString(), "GENERATE", saved);

                        return saved;
                });
        }

        @Transactional
        public Invoice generateFromOrder(Long orderId) {
                return invoiceGenerationTimer.record(() -> {
                        Long tenantId = TenantContext.getTenantId();
                        Order order = orderRepository
                                        .findByIdAndTenantId(orderId, tenantId)
                                        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

                        if (OrderType.SALE != order.getType()) {
                                throw new IllegalArgumentException("Invoice can only be created for SALE orders");
                        }

                        var existing = invoiceRepository.findByTenantIdAndOrderId(tenantId, order.getId());
                        if (existing.isPresent()) {
                                log.warn("Invoice already exists for order {}, returning existing one", order.getId());
                                return existing.get();
                        }

                        String invoiceNumber = incrementAndGetInvoiceNumber();

                        Invoice invoice = Invoice.builder()
                                        .tenantId(TenantContext.getTenantId())
                                        .orderId(order.getId())
                                        .invoiceNumber(invoiceNumber)
                                        .amount(order.getTotalAmount())
                                        .taxAmount(order.getTaxAmount())
                                        .discount(order.getDiscount())
                                        .status("UNPAID")
                                        .dueDate(LocalDate.now().plusDays(DEFAULT_DUE_DAYS))
                                        .build();

                        if (invoice.getTenantId() == null) {
                                throw new IllegalStateException("TenantContext missing - cannot create invoice");
                        }

                        log.info("Invoice created from order: {} for order {}", invoiceNumber, order.getId());
                        Invoice saved = invoiceRepository.save(invoice);

                        invoiceGeneratedCounter.increment();
                        outboxService.saveEvent("INVOICE", saved.getId().toString(), "GENERATE", saved);

                        return saved;
                });
        }

        @Transactional
        public Invoice voidInvoice(Long id) {
                Long tenantId = TenantContext.getTenantId();
                Invoice invoice = invoiceRepository
                                .findByIdAndTenantId(id, tenantId)
                                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

                if ("PAID".equals(invoice.getStatus())) {
                        throw new IllegalArgumentException("Cannot void a PAID invoice");
                }
                if ("VOIDED".equals(invoice.getStatus())) {
                        throw new IllegalArgumentException("Invoice is already voided");
                }

                invoice.setStatus("VOIDED");
                Invoice saved = invoiceRepository.save(invoice);
                invoiceVoidedCounter.increment();
                log.info("Invoice voided: {}", invoice.getInvoiceNumber());

                return saved;
        }

        @Transactional
        public Invoice updateStatus(Long id, InvoiceStatusRequest request) {
                Long tenantId = TenantContext.getTenantId();
                Invoice invoice = invoiceRepository
                                .findByIdAndTenantId(id, tenantId)
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
                if (OrderType.SALE != order.getType()) {
                        throw new IllegalArgumentException("Invoice can only be created for SALE orders");
                }

                Long tenantId = TenantContext.getTenantId();
                var existing = invoiceRepository.findByTenantIdAndOrderId(tenantId, order.getId());
                if (existing.isPresent()) {
                        log.warn("Invoice already exists for order {}, returning existing one", order.getId());
                        return existing.get();
                }

                String invoiceNumber = incrementAndGetInvoiceNumber();

                Invoice invoice = Invoice.builder()
                                .tenantId(TenantContext.getTenantId())
                                .orderId(order.getId())
                                .invoiceNumber(invoiceNumber)
                                .amount(order.getTotalAmount())
                                .taxAmount(order.getTaxAmount())
                                .discount(order.getDiscount())
                                .status("UNPAID")
                                .dueDate(LocalDate.now().plusDays(DEFAULT_DUE_DAYS))
                                .build();

                if (invoice.getTenantId() == null) {
                        throw new IllegalStateException("TenantContext missing - cannot create invoice");
                }

                log.info("Invoice created: {} for order {}", invoiceNumber, order.getId());
                Invoice saved = invoiceRepository.save(invoice);

                // Trigger async PDF generation or other downstream tasks
                outboxService.saveEvent("INVOICE", saved.getId().toString(), "GENERATE", saved);

                return saved;
        }

        @Transactional
        public Invoice createCreditNote(Order returnOrder, Long parentInvoiceId) {
                String invoiceNumber = "CN-" + incrementAndGetInvoiceNumber().substring(4);

                Invoice creditNote = Invoice.builder()
                                .tenantId(TenantContext.getTenantId())
                                .orderId(returnOrder.getId())
                                .invoiceNumber(invoiceNumber)
                                .amount(returnOrder.getTotalAmount().negate())
                                .taxAmount(returnOrder.getTaxAmount() != null ? returnOrder.getTaxAmount().negate()
                                                : BigDecimal.ZERO)
                                .discount(returnOrder.getDiscount() != null ? returnOrder.getDiscount().negate()
                                                : BigDecimal.ZERO)
                                .status("PAID")
                                .parentInvoiceId(parentInvoiceId)
                                .dueDate(LocalDate.now())
                                .paidAt(LocalDateTime.now())
                                .build();

                if (creditNote.getTenantId() == null) {
                        throw new IllegalStateException("TenantContext missing - cannot create credit note");
                }

                log.info("Credit note created: {} for return order {}", invoiceNumber, returnOrder.getId());
                return invoiceRepository.save(creditNote);
        }

        private String incrementAndGetInvoiceNumber() {
                // Harden sequence generation with a pessimistic lock on the tenant
                Tenant tenant = tenantRepository
                                .findByIdWithLock(Objects.requireNonNull(TenantContext.getTenantId()))
                                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

                Integer currentSeq = tenant.getInvoiceSequence() != null ? tenant.getInvoiceSequence() : 0;
                tenant.setInvoiceSequence(currentSeq + 1);
                tenantRepository.save(tenant);

                String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                return String.format("INV-%d-%s-%04d", tenant.getId(), dateStr, tenant.getInvoiceSequence());
        }

        @Transactional(readOnly = true)
        public byte[] generatePdf(Long id) {
                return pdfGenerationTimer.record(() -> {
                        try {
                                Long tenantId = TenantContext.getTenantId();
                                Invoice invoice = invoiceRepository
                                                .findByIdAndTenantId(id, tenantId)
                                                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

                                Order order = orderRepository
                                                .findByIdAndTenantId(Objects.requireNonNull(invoice.getOrderId()),
                                                                tenantId)
                                                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

                                Tenant tenant = tenantRepository
                                                .findById(tenantId)
                                                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

                                Customer customer = customerRepository
                                                .findByIdAndTenantId(Objects.requireNonNull(order.getCustomerId()),
                                                                tenantId)
                                                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));

                                List<OrderItem> orderItems = orderItemRepository.findByOrderIdAndTenantId(order.getId(),
                                                tenantId);

                                List<Map<String, Object>> items = orderItems.stream()
                                                .map(item -> {
                                                        Product product = productRepository
                                                                        .findByIdAndTenantIdAndIsDeletedFalse(
                                                                                        Objects.requireNonNull(item
                                                                                                        .getProductId()),
                                                                                        tenantId)
                                                                        .orElseThrow(() -> new EntityNotFoundException(
                                                                                        "Product not found"));

                                                        Map<String, Object> map = new HashMap<>();
                                                        map.put("productName", product.getName());
                                                        map.put("quantity", item.getQuantity());
                                                        map.put("unitPrice", item.getUnitPrice());
                                                        map.put("discount", item.getDiscount());
                                                        map.put("total", item.getSubtotal());
                                                        return map;
                                                })
                                                .collect(Collectors.toList());

                                Context context = new Context();
                                context.setVariable("tenantName", tenant.getName());
                                context.setVariable("tenantAddress",
                                                tenant.getAddress() != null ? tenant.getAddress()
                                                                : "Company Address TBD");
                                context.setVariable("tenantGstin",
                                                tenant.getGstin() != null ? tenant.getGstin() : "GSTIN-TBD");

                                context.setVariable("customerName", customer.getName());
                                context.setVariable("customerAddress", customer.getAddress());
                                context.setVariable("customerGstin", customer.getGstin());

                                context.setVariable("invoiceNumber", invoice.getInvoiceNumber());
                                context.setVariable(
                                                "invoiceDate",
                                                invoice.getCreatedAt() != null ? invoice.getCreatedAt().toLocalDate()
                                                                : LocalDate.now());
                                context.setVariable("orderId", order.getId());
                                context.setVariable("status", invoice.getStatus());

                                context.setVariable("items", items);
                                context.setVariable(
                                                "subtotal",
                                                order.getTotalAmount().subtract(order.getTaxAmount())
                                                                .add(order.getDiscount()));
                                context.setVariable("taxAmount", order.getTaxAmount());
                                context.setVariable("discount", order.getDiscount());
                                context.setVariable("totalAmount", order.getTotalAmount());

                                return pdfService.generatePdfFromHtml("invoice-template", context);
                        } catch (Exception e) {
                                pdfGenerationFailureCounter.increment();
                                log.error("PDF generation failed for invoice {}", id, e);
                                throw new RuntimeException("PDF generation failed", e);
                        }
                });
        }

        public Page<Invoice> getInvoices(Pageable pageable) {
                Long tenantId = TenantContext.getTenantId();
                return invoiceRepository.findAllByTenantId(tenantId, pageable);
        }

        public Page<Invoice> getOverdueInvoices(Pageable pageable) {
                Long tenantId = TenantContext.getTenantId();
                return invoiceRepository.findByTenantIdAndStatusNotAndDueDateBefore(tenantId, "PAID", LocalDate.now(),
                                pageable);
        }

        public Invoice getInvoiceById(Long id) {
                Long tenantId = TenantContext.getTenantId();
                return invoiceRepository
                                .findByIdAndTenantId(id, tenantId)
                                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));
        }
}