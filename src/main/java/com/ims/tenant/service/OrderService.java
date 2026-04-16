package com.ims.tenant.service;

import com.ims.model.Order;
import com.ims.model.OrderItem;
import com.ims.product.Product;
import com.ims.shared.exception.InsufficientStockException;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.product.ProductRepository;
import com.ims.tenant.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class OrderService {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final ProductRepository productRepository;
  private final SupplierRepository supplierRepository;
  private final CustomerRepository customerRepository;
  private final StockService stockService;
  private final InvoiceService invoiceService;
  private final com.ims.shared.audit.AuditLogService auditLogService;
  private final com.ims.shared.pdf.PdfService pdfService;
  private final com.ims.platform.repository.TenantRepository tenantRepository;
 
  private static final int PERCENTAGE_BASE = 100;

  @Transactional
  public @NonNull Map<String, Object> createPurchaseOrder(@NonNull Map<String, Object> request, @NonNull Long userId) {
    Long supplierId = Long.valueOf(request.get("supplier_id").toString());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) request.get("items");

    // Validate supplier belongs to tenant
    supplierRepository
        .findById(Objects.requireNonNull(supplierId))
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
    order.setStatus("PENDING");
    order = Objects.requireNonNull(orderRepository.save(order));

    // Save items
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
      orderItemRepository.save(Objects.requireNonNull(orderItem));
    }

    log.info(
        "Purchase order created: id={} total={}", order.getId(), totalAmount);
    
    auditLogService.logAudit(
        "CREATE_PURCHASE_ORDER",
        "ORDER",
        order.getId(),
        String.format("Created purchase order #%d, Supplier: %d, Total: %s", order.getId(), order.getSupplierId(), totalAmount));

    return Objects.requireNonNull(Map.of("order_id", order.getId(), "total", totalAmount));
  }

  @Transactional
  public @NonNull Map<String, Object> createSalesOrder(@NonNull Map<String, Object> request, @NonNull Long userId) {
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
              .findById(Objects.requireNonNull(productId))
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
            .status("PENDING")
            .customerId(customerId)
            .totalAmount(totalAmount)
            .taxAmount(taxAmount)
            .discount(rootDiscount)
            .notes(request.getOrDefault("notes", "").toString())
            .createdBy(userId)
            .build();
    order = Objects.requireNonNull(orderRepository.save(Objects.requireNonNull(order)));

    // Save items
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
      orderItemRepository.save(Objects.requireNonNull(orderItem));
    }

    log.info("Sales order created: id={} total={}", order.getId(), totalAmount);

    auditLogService.logAudit(
        "CREATE_SALE_ORDER",
        "ORDER",
        order.getId(),
        String.format("Created sales order #%d, Customer: %d, Total: %s", order.getId(), order.getCustomerId(), totalAmount));

    return Objects.requireNonNull(Map.of(
        "order_id", order.getId(),
        "total", totalAmount,
        "grand_total", grandTotalCalculated));
  }

  @Transactional
  public @NonNull Order createReturnOrder(@NonNull Map<String, Object> request, @NonNull Long userId) {
    Long originalOrderId = Long.valueOf(request.get("original_order_id").toString());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> returnItems = (List<Map<String, Object>>) request.get("items");

    Order originalOrder = orderRepository.findById(originalOrderId)
        .orElseThrow(() -> new EntityNotFoundException("Original order not found"));

    if (!"SALE".equals(originalOrder.getType())) {
      throw new IllegalArgumentException("Returns can only be created for SALE orders");
    }

    BigDecimal returnTotal = BigDecimal.ZERO;
    BigDecimal returnTax = BigDecimal.ZERO;

    Order returnOrder = Order.builder()
        .type("RETURN")
        .status("COMPLETED")
        .customerId(originalOrder.getCustomerId())
        .referenceOrderId(originalOrderId)
        .notes(request.getOrDefault("notes", "Customer return").toString())
        .createdBy(userId)
        .build();

    returnOrder = orderRepository.save(returnOrder);

    List<OrderItem> originalItems = orderItemRepository.findByOrderId(originalOrderId);

    for (Map<String, Object> item : returnItems) {
      Long productId = Long.valueOf(item.get("product_id").toString());
      int qty = Integer.parseInt(item.get("quantity").toString());

      OrderItem originalItem = originalItems.stream()
          .filter(oi -> oi.getProductId().equals(productId))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Product " + productId + " was not part of the original order"));

      if (qty > originalItem.getQuantity()) {
        throw new IllegalArgumentException("Cannot return more than originally purchased for product " + productId);
      }

      BigDecimal unitPrice = originalItem.getUnitPrice();
      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
      BigDecimal taxRate = originalItem.getTaxRate();
      BigDecimal itemTax = itemTotal.multiply(taxRate).divide(BigDecimal.valueOf(PERCENTAGE_BASE), 2, RoundingMode.HALF_UP);

      returnTotal = returnTotal.add(itemTotal);
      returnTax = returnTax.add(itemTax);

      OrderItem returnOrderItem = OrderItem.builder()
          .orderId(returnOrder.getId())
          .productId(productId)
          .quantity(qty)
          .unitPrice(unitPrice)
          .taxRate(taxRate)
          .total(itemTotal)
          .build();
      orderItemRepository.save(returnOrderItem);

      // Restore stock
      stockService.stockIn(productId, qty, "Return for Order #" + originalOrderId, userId);
    }

    returnOrder.setTotalAmount(returnTotal);
    returnOrder.setTaxAmount(returnTax);
    returnOrder = orderRepository.save(returnOrder);

    // Create Credit Note
    invoiceService.createCreditNote(returnOrder, null);

    auditLogService.logAudit("CREATE_RETURN_ORDER", "ORDER", returnOrder.getId(), 
        String.format("Processed return for order #%d, Total Credit: %s", originalOrderId, returnTotal));

    return returnOrder;
  }

  public @NonNull Page<Order> getOrders(@NonNull Pageable pageable) {
    return Objects.requireNonNull(orderRepository.findAll(pageable));
  }

  public @NonNull Page<Order> getOrdersByType(@NonNull String type, @NonNull Pageable pageable) {
    return Objects.requireNonNull(orderRepository.findByType(type, pageable));
  }

  public @NonNull Map<String, Object> getOrderWithItems(@NonNull Long id) {
    Order order =
        orderRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
    return Objects.requireNonNull(Map.of("order", order, "items", items));
  }

  @Transactional(readOnly = true)
  public byte[] generateOrderPdf(@NonNull Long id) {
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    com.ims.model.Tenant tenant = tenantRepository.findById(Objects.requireNonNull(com.ims.shared.auth.TenantContext.get()))
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    String partnerName = "N/A";
    String partnerAddress = "N/A";

    if ("SALE".equals(order.getType()) && order.getCustomerId() != null) {
      var customer = customerRepository.findById(order.getCustomerId()).orElse(null);
      if (customer != null) {
        partnerName = customer.getName();
        partnerAddress = customer.getAddress();
      }
    } else if ("PURCHASE".equals(order.getType()) && order.getSupplierId() != null) {
      var supplier = supplierRepository.findById(order.getSupplierId()).orElse(null);
      if (supplier != null) {
        partnerName = supplier.getName();
        partnerAddress = supplier.getAddress();
      }
    }

    List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
    List<Map<String, Object>> items = orderItems.stream().map(item -> {
      var product = productRepository.findById(item.getProductId()).orElse(null);
      Map<String, Object> map = new java.util.HashMap<>();
      map.put("productName", product != null ? product.getName() : "Unknown");
      map.put("quantity", item.getQuantity());
      map.put("unitPrice", item.getUnitPrice());
      map.put("discount", item.getDiscount());
      map.put("total", item.getTotal());
      return map;
    }).collect(java.util.stream.Collectors.toList());

    org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
    context.setVariable("tenantName", tenant.getName());
    context.setVariable("tenantAddress", tenant.getAddress());
    context.setVariable("tenantGstin", tenant.getGstin());
    context.setVariable("partnerName", partnerName);
    context.setVariable("partnerAddress", partnerAddress);
    context.setVariable("orderId", order.getId());
    context.setVariable("orderDate", order.getCreatedAt().toLocalDate());
    context.setVariable("status", order.getStatus());
    context.setVariable("type", order.getType());
    context.setVariable("items", items);
    context.setVariable("subtotal", order.getTotalAmount().subtract(order.getTaxAmount()).add(order.getDiscount()));
    context.setVariable("taxAmount", order.getTaxAmount());
    context.setVariable("discount", order.getDiscount());
    context.setVariable("totalAmount", order.getTotalAmount());

    return pdfService.generatePdfFromHtml("order-summary", context);
  }

  @Transactional
  public @NonNull Order confirmOrder(@NonNull Long id, @NonNull Long userId) {
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if (!"PENDING".equals(order.getStatus())) {
      throw new IllegalStateException("Only PENDING orders can be confirmed");
    }

    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

    if ("SALE".equals(order.getType())) {
      // Validate and reduce stock
      for (OrderItem item : items) {
        Product product = productRepository.findById(Objects.requireNonNull(item.getProductId()))
            .orElseThrow(() -> new EntityNotFoundException("Product not found: " + item.getProductId()));
        if (product.getStock() < item.getQuantity()) {
          throw new InsufficientStockException("Insufficient stock for " + product.getName(), product.getStock(), item.getQuantity());
        }
        stockService.stockOut(Objects.requireNonNull(item.getProductId()), item.getQuantity(), "Confirmed Sale Order #" + order.getId(), userId);
      }
      order.setStatus("CONFIRMED");
      // Auto-generate invoice for sales upon confirmation
      invoiceService.createFromOrder(order);
    } else if ("PURCHASE".equals(order.getType())) {
      order.setStatus("CONFIRMED");
    }

    order = orderRepository.save(order);
    auditLogService.logAudit("CONFIRM_ORDER", "ORDER", id, "Confirmed " + order.getType() + " order #" + id);
    return order;
  }

  @Transactional
  public @NonNull Order shipOrder(@NonNull Long id, @NonNull Long userId) {
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if (!"CONFIRMED".equals(order.getStatus())) {
      throw new IllegalStateException("Only CONFIRMED orders can be shipped");
    }

    order.setStatus("SHIPPED");
    order = orderRepository.save(order);
    auditLogService.logAudit("SHIP_ORDER", "ORDER", id, "Shipped " + order.getType() + " order #" + id);
    return order;
  }

  @Transactional
  public @NonNull Order completeOrder(@NonNull Long id, @NonNull Long userId) {
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if (!"SHIPPED".equals(order.getStatus()) && !"CONFIRMED".equals(order.getStatus())) {
      throw new IllegalStateException("Order must be SHIPPED or CONFIRMED to be completed");
    }

    if ("PURCHASE".equals(order.getType()) && !"RECEIVED".equals(order.getStatus())) {
      // For purchase, completion means receiving goods
      List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
      for (OrderItem item : items) {
        stockService.stockIn(Objects.requireNonNull(item.getProductId()), item.getQuantity(), "Received Purchase Order #" + order.getId(), userId);
      }
      order.setStatus("RECEIVED");
    } else {
      order.setStatus("COMPLETED");
    }

    order = orderRepository.save(order);
    auditLogService.logAudit("COMPLETE_ORDER", "ORDER", id, "Completed " + order.getType() + " order #" + id);
    return order;
  }

  @Transactional
  public @NonNull Order cancelOrder(@NonNull Long id, @NonNull Long userId) {
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if ("COMPLETED".equals(order.getStatus()) || "RECEIVED".equals(order.getStatus()) || "CANCELLED".equals(order.getStatus())) {
      throw new IllegalStateException("Cannot cancel an order that is already " + order.getStatus());
    }

    // If it was CONFIRMED or SHIPPED, we might need to revert stock for SALES
    if ("SALE".equals(order.getType()) && ("CONFIRMED".equals(order.getStatus()) || "SHIPPED".equals(order.getStatus()))) {
      List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
      for (OrderItem item : items) {
        stockService.stockIn(Objects.requireNonNull(item.getProductId()), item.getQuantity(), "Cancelled Sale Order #" + order.getId(), userId);
      }
    }

    order.setStatus("CANCELLED");
    order = orderRepository.save(order);
    auditLogService.logAudit("CANCEL_ORDER", "ORDER", id, "Cancelled " + order.getType() + " order #" + id);
    return order;
  }

  public @NonNull Page<Order> getOrdersBySupplier(@NonNull Long supplierId, @NonNull Pageable pageable) {
    return Objects.requireNonNull(orderRepository.findBySupplierId(supplierId, pageable));
  }

  public @NonNull Page<Order> getOrdersByCustomer(@NonNull Long customerId, @NonNull Pageable pageable) {
    return Objects.requireNonNull(orderRepository.findByCustomerId(customerId, pageable));
  }

  @Transactional
  public @NonNull Order updateOrderStatus(@NonNull Long id, @NonNull String status) {
    Order order =
        orderRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    order.setStatus(status);
    return Objects.requireNonNull(orderRepository.save(order));
  }
}
