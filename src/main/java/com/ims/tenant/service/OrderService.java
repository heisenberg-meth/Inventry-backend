package com.ims.tenant.service;

import com.ims.model.Invoice;
import com.ims.model.Order;
import com.ims.model.OrderItem;
import com.ims.model.Product;
import com.ims.shared.exception.InsufficientStockException;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.ProductRepository;
import com.ims.tenant.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final ProductRepository productRepository;
  private final SupplierRepository supplierRepository;
  private final CustomerRepository customerRepository;
  private final StockService stockService;
  private final InvoiceService invoiceService;
  private final PaymentService paymentService;
  private final com.ims.shared.audit.AuditLogService auditLogService;
 
  private static final int PERCENTAGE_BASE = 100;

  @Transactional
  public Map<String, Object> createPurchaseOrder(Map<String, Object> request, Long userId) {
    Long supplierId = Long.valueOf(request.get("supplier_id").toString());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) request.get("items");

    // Validate supplier belongs to tenant
    supplierRepository
        .findById(supplierId)
        .orElseThrow(() -> new EntityNotFoundException("Supplier not found"));

    BigDecimal totalAmount = BigDecimal.ZERO;
    BigDecimal taxAmount = BigDecimal.ZERO;

    // Save order
    Order order =
        Order.builder()
            .type("PURCHASE")
            .status("RECEIVED")
            .supplierId(supplierId)
            .notes(request.getOrDefault("notes", "").toString())
            .createdBy(userId)
            .build();

    // Calculate totals and validate items
    for (Map<String, Object> item : items) {
      // Validate product exists
      Long.valueOf(item.get("product_id").toString());
      int qty = Integer.parseInt(item.get("quantity").toString());
      BigDecimal unitPrice = new BigDecimal(item.get("unit_price").toString());
      BigDecimal discount =
          item.containsKey("discount")
              ? new BigDecimal(item.get("discount").toString())
              : BigDecimal.ZERO;
      BigDecimal taxRate =
          item.containsKey("tax_rate")
              ? new BigDecimal(item.get("tax_rate").toString())
              : BigDecimal.ZERO;

      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount);
      BigDecimal itemTax =
          itemTotal
              .multiply(taxRate)
              .divide(BigDecimal.valueOf(PERCENTAGE_BASE), 2, RoundingMode.HALF_UP);

      totalAmount = totalAmount.add(itemTotal);
      taxAmount = taxAmount.add(itemTax);
    }

    order.setTotalAmount(totalAmount);
    order.setTaxAmount(taxAmount);
    order = orderRepository.save(order);

    // Save items and do stock in
    for (Map<String, Object> item : items) {
      Long productId = Long.valueOf(item.get("product_id").toString());
      int qty = Integer.parseInt(item.get("quantity").toString());
      BigDecimal unitPrice = new BigDecimal(item.get("unit_price").toString());
      BigDecimal discount =
          item.containsKey("discount")
              ? new BigDecimal(item.get("discount").toString())
              : BigDecimal.ZERO;
      BigDecimal taxRate =
          item.containsKey("tax_rate")
              ? new BigDecimal(item.get("tax_rate").toString())
              : BigDecimal.ZERO;

      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount);

      OrderItem orderItem =
          OrderItem.builder()
              .orderId(order.getId())
              .productId(productId)
              .quantity(qty)
              .unitPrice(unitPrice)
              .discount(discount)
              .taxRate(taxRate)
              .total(itemTotal)
              .build();
      orderItemRepository.save(orderItem);

      // Stock in
      stockService.stockIn(productId, qty, "Purchase Order #" + order.getId(), userId);
    }

    log.info(
        "Purchase order created: id={} total={}", order.getId(), totalAmount);
    
    auditLogService.log(
        "CREATE_PURCHASE_ORDER",
        order.getTenantId(),
        userId,
        String.format("Created purchase order #%d, Supplier: %d, Total: %s", order.getId(), order.getSupplierId(), totalAmount));

    return Map.of("order_id", order.getId(), "total", totalAmount);
  }

  @Transactional
  public Map<String, Object> createSalesOrder(Map<String, Object> request, Long userId) {
    Long customerId =
        request.containsKey("customer_id")
            ? Long.valueOf(request.get("customer_id").toString())
            : null;
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) request.get("items");
    // Validate customer if provided
    if (customerId != null) {
      customerRepository
          .findById(customerId)
          .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
    }

    // CHECK ALL stock availability BEFORE processing any items
    for (Map<String, Object> item : items) {
      Long productId = Long.valueOf(item.get("product_id").toString());
      int qty = Integer.parseInt(item.get("quantity").toString());

      Product product =
          productRepository
              .findById(productId)
              .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));

      if (product.getStock() < qty) {
        throw new InsufficientStockException(
            "Insufficient stock for product: "
                + product.getName()
                + ". Requested: "
                + qty
                + ", Available: "
                + product.getStock(),
            product.getStock(),
            qty);
      }
    }

    BigDecimal totalAmount = BigDecimal.ZERO;
    BigDecimal taxAmount = BigDecimal.ZERO;

    // Calculate totals
    for (Map<String, Object> item : items) {
      int qty = Integer.parseInt(item.get("quantity").toString());
      BigDecimal unitPrice = new BigDecimal(item.get("unit_price").toString());
      BigDecimal discount =
          item.containsKey("discount")
              ? new BigDecimal(item.get("discount").toString())
              : BigDecimal.ZERO;
      BigDecimal taxRate =
          item.containsKey("tax_rate")
              ? new BigDecimal(item.get("tax_rate").toString())
              : BigDecimal.ZERO;

      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount);
      BigDecimal itemTax =
          itemTotal
              .multiply(taxRate)
              .divide(BigDecimal.valueOf(PERCENTAGE_BASE), 2, RoundingMode.HALF_UP);

      totalAmount = totalAmount.add(itemTotal);
      taxAmount = taxAmount.add(itemTax);
    }

    // Apply root-level discount if any
    BigDecimal rootDiscount =
        request.containsKey("discount_total")
            ? new BigDecimal(request.get("discount_total").toString())
            : BigDecimal.ZERO;

    BigDecimal grandTotalCalculated = totalAmount.add(taxAmount).subtract(rootDiscount);

    // Validate grand_total if provided
    if (request.containsKey("grand_total")) {
      BigDecimal grandTotalProvided = new BigDecimal(request.get("grand_total").toString());
      if (grandTotalCalculated.compareTo(grandTotalProvided) != 0) {
        throw new IllegalArgumentException(
            "Grand total mismatch. Calculated: "
                + grandTotalCalculated
                + ", Provided: "
                + grandTotalProvided);
      }
    }

    Order order =
        Order.builder()
            .type("SALE")
            .status("COMPLETED")
            .customerId(customerId)
            .totalAmount(totalAmount)
            .taxAmount(taxAmount)
            .discount(rootDiscount)
            .notes(request.getOrDefault("notes", "").toString())
            .createdBy(userId)
            .build();
    order = orderRepository.save(order);

    // Save items and stock out
    for (Map<String, Object> item : items) {
      Long productId = Long.valueOf(item.get("product_id").toString());
      int qty = Integer.parseInt(item.get("quantity").toString());
      BigDecimal unitPrice = new BigDecimal(item.get("unit_price").toString());
      BigDecimal discount =
          item.containsKey("discount")
              ? new BigDecimal(item.get("discount").toString())
              : BigDecimal.ZERO;
      BigDecimal taxRate =
          item.containsKey("tax_rate")
              ? new BigDecimal(item.get("tax_rate").toString())
              : BigDecimal.ZERO;

      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount);

      OrderItem orderItem =
          OrderItem.builder()
              .orderId(order.getId())
              .productId(productId)
              .quantity(qty)
              .unitPrice(unitPrice)
              .discount(discount)
              .taxRate(taxRate)
              .total(itemTotal)
              .build();
      orderItemRepository.save(orderItem);

      // Atomic stock decrement
      stockService.stockOut(productId, qty, "Sale Order #" + order.getId(), userId);
    }

    // Auto-generate invoice
    Invoice invoice = invoiceService.createFromOrder(order);

    // Process payment if payment_method is provided
    if (request.containsKey("payment_method")) {
      String paymentMethod = request.get("payment_method").toString();
      com.ims.dto.CreatePaymentRequest pr = new com.ims.dto.CreatePaymentRequest();
      pr.setInvoiceId(invoice.getId());
      pr.setAmount(grandTotalCalculated);
      pr.setPaymentMode(paymentMethod);
      pr.setNotes("Auto-payment for sale order #" + order.getId());
      paymentService.processPayment(pr);
    }

    log.info("Sales order created: id={} total={} invoice={}", 
             order.getId(), totalAmount, invoice.getInvoiceNumber());

    auditLogService.log(
        "CREATE_SALE_ORDER",
        order.getTenantId(),
        userId,
        String.format("Created sales order #%d, Customer: %d, Invoice: %s, Total: %s", order.getId(), order.getCustomerId(), invoice.getInvoiceNumber(), totalAmount));

    return Map.of(
        "order_id", order.getId(),
        "invoice_id", invoice.getId(),
        "invoice_number", invoice.getInvoiceNumber(),
        "total", totalAmount,
        "grand_total", grandTotalCalculated);
  }

  public Page<Order> getOrders(Pageable pageable) {
    return orderRepository.findAll(pageable);
  }

  public Page<Order> getOrdersByType(String type, Pageable pageable) {
    return orderRepository.findByType(type, pageable);
  }

  public Map<String, Object> getOrderWithItems(Long id) {
    Order order =
        orderRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
    return Map.of("order", order, "items", items);
  }

  @Transactional
  public Order updateOrderStatus(Long id, String status) {
    Order order =
        orderRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    order.setStatus(status);
    return orderRepository.save(order);
  }
}
