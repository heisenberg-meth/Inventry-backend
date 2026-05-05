package com.ims.tenant.service;

import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditResource;
import com.ims.shared.auth.TenantContext;
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
  private final com.ims.shared.audit.AuditLogService auditLogService;
  private final com.ims.shared.pdf.PdfService pdfService;
  private final com.ims.platform.repository.TenantRepository tenantRepository;

  private static final int PERCENTAGE_BASE = 100;

  @Transactional
  public Map<String, Object> createPurchaseOrder(Map<String, Object> request, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    Long supplierId = Long.valueOf(request.get("supplier_id").toString());

    var supplierOpt = supplierRepository.findActiveByIdAndTenantId(supplierId, tenantId);
    if (supplierOpt.isEmpty()) {
      throw new EntityNotFoundException("Supplier not found or does not belong to your tenant");
    }

    Object rawItems = request.get("items");
    if (!(rawItems instanceof List<?> list)) {
      throw new IllegalArgumentException("Invalid items format: expected list");
    }
    List<Map<String, Object>> items = new java.util.ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        throw new IllegalArgumentException("Invalid item structure: expected object");
      }
      Map<String, Object> typedMap = new java.util.HashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!(entry.getKey() instanceof String key)) {
          throw new IllegalArgumentException("Invalid key type in item: expected String");
        }
        typedMap.put(key, entry.getValue());
      }
      items.add(typedMap);
    }

    BigDecimal totalAmount = BigDecimal.ZERO;
    BigDecimal taxAmount = BigDecimal.ZERO;

    // Save order
    Order order = Order.builder()
        .type("PURCHASE")
        .status("RECEIVED")
        .tenantId(com.ims.shared.auth.TenantContext.getTenantId())
        .supplierId(supplierId)
        .notes(request.getOrDefault("notes", "").toString())
        .createdBy(userId)
        .build();

    if (order.getTenantId() == null) {
      throw new IllegalStateException("TenantContext missing - cannot create purchase order");
    }

    // Calculate totals and validate items
    for (Map<String, Object> item : items) {
      // Validate product exists
      Long.valueOf(item.get("product_id").toString());
      int qty = Integer.parseInt(item.get("quantity").toString());
      BigDecimal unitPrice = new BigDecimal(item.get("unit_price").toString());
      BigDecimal discount = item.containsKey("discount")
          ? new BigDecimal(item.get("discount").toString())
          : BigDecimal.ZERO;
      BigDecimal taxRate = item.containsKey("tax_rate")
          ? new BigDecimal(item.get("tax_rate").toString())
          : BigDecimal.ZERO;

      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount);
      BigDecimal itemTax = itemTotal
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
      BigDecimal discount = item.containsKey("discount")
          ? new BigDecimal(item.get("discount").toString())
          : BigDecimal.ZERO;
      BigDecimal taxRate = item.containsKey("tax_rate")
          ? new BigDecimal(item.get("tax_rate").toString())
          : BigDecimal.ZERO;

      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount);

      OrderItem orderItem = OrderItem.builder()
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
        AuditAction.CREATE_PURCHASE_ORDER,
        AuditResource.ORDER,
        order.getId(),
        String.format("Created purchase order #%d, Supplier: %d, Total: %s", order.getId(), order.getSupplierId(),
            totalAmount));

    return Objects.requireNonNull(Map.of("order_id", order.getId(), "total", totalAmount));
  }

  @Transactional
  public Map<String, Object> createSalesOrder(Map<String, Object> request, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    Long customerId = request.containsKey("customer_id")
        ? Long.valueOf(request.get("customer_id").toString())
        : null;
    Object rawItems = request.get("items");
    if (!(rawItems instanceof List<?> list)) {
      throw new IllegalArgumentException("Invalid items format: expected list");
    }
    List<Map<String, Object>> items = new java.util.ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        throw new IllegalArgumentException("Invalid item structure: expected object");
      }
      Map<String, Object> typedMap = new java.util.HashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!(entry.getKey() instanceof String key)) {
          throw new IllegalArgumentException("Invalid key type in item: expected String");
        }
        typedMap.put(key, entry.getValue());
      }
      items.add(typedMap);
    }
    // Validate customer belongs to tenant if provided
    if (customerId != null) {
      customerRepository
          .findByIdAndTenantId(customerId, tenantId)
          .orElseThrow(() -> new EntityNotFoundException("Customer not found or does not belong to your tenant"));
    }

    // CHECK ALL stock availability BEFORE processing any items
    for (Map<String, Object> item : items) {
      Long productId = Long.valueOf(item.get("product_id").toString());
      int qty = Integer.parseInt(item.get("quantity").toString());

      Product product = productRepository
          .findByIdAndTenantId(productId, tenantId)
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
      BigDecimal discount = item.containsKey("discount")
          ? new BigDecimal(item.get("discount").toString())
          : BigDecimal.ZERO;
      BigDecimal taxRate = item.containsKey("tax_rate")
          ? new BigDecimal(item.get("tax_rate").toString())
          : BigDecimal.ZERO;

      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount);
      BigDecimal itemTax = itemTotal
          .multiply(taxRate)
          .divide(BigDecimal.valueOf(PERCENTAGE_BASE), 2, RoundingMode.HALF_UP);

      totalAmount = totalAmount.add(itemTotal);
      taxAmount = taxAmount.add(itemTax);
    }

    // Apply root-level discount if any
    BigDecimal rootDiscount = request.containsKey("discount_total")
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

    Order order = Order.builder()
        .type("SALE")
        .status("PENDING")
        .tenantId(com.ims.shared.auth.TenantContext.getTenantId())
        .customerId(customerId)
        .totalAmount(totalAmount)
        .taxAmount(taxAmount)
        .discount(rootDiscount)
        .notes(request.getOrDefault("notes", "").toString())
        .createdBy(userId)
        .build();

    if (order.getTenantId() == null) {
      throw new IllegalStateException("TenantContext missing - cannot create sales order");
    }
    order = Objects.requireNonNull(orderRepository.save(Objects.requireNonNull(order)));

    // Save items
    for (Map<String, Object> item : items) {
      Long productId = Long.valueOf(item.get("product_id").toString());
      int qty = Integer.parseInt(item.get("quantity").toString());
      BigDecimal unitPrice = new BigDecimal(item.get("unit_price").toString());
      BigDecimal discount = item.containsKey("discount")
          ? new BigDecimal(item.get("discount").toString())
          : BigDecimal.ZERO;
      BigDecimal taxRate = item.containsKey("tax_rate")
          ? new BigDecimal(item.get("tax_rate").toString())
          : BigDecimal.ZERO;

      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount);

      OrderItem orderItem = OrderItem.builder()
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
        AuditAction.CREATE_SALE_ORDER,
        AuditResource.ORDER,
        order.getId(),
        String.format("Created sales order #%d, Customer: %d, Total: %s", order.getId(), order.getCustomerId(),
            totalAmount));

    return Objects.requireNonNull(Map.of(
        "order_id", order.getId(),
        "total", totalAmount,
        "grand_total", grandTotalCalculated));
  }

  @Transactional
  public Order createReturnOrder(Map<String, Object> request, Long userId) {
    Long originalOrderId = Long.valueOf(request.get("original_order_id").toString());
    Object rawItems = request.get("items");
    if (!(rawItems instanceof List<?> list)) {
      throw new IllegalArgumentException("Invalid items format: expected list");
    }
    List<Map<String, Object>> returnItems = new java.util.ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        throw new IllegalArgumentException("Invalid item structure: expected object");
      }
      Map<String, Object> typedMap = new java.util.HashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!(entry.getKey() instanceof String key)) {
          throw new IllegalArgumentException("Invalid key type in item: expected String");
        }
        typedMap.put(key, entry.getValue());
      }
      returnItems.add(typedMap);
    }

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
        .tenantId(com.ims.shared.auth.TenantContext.getTenantId())
        .customerId(originalOrder.getCustomerId())
        .referenceOrderId(originalOrderId)
        .notes(request.getOrDefault("notes", "Customer return").toString())
        .createdBy(userId)
        .build();

    if (returnOrder.getTenantId() == null) {
      throw new IllegalStateException("TenantContext missing - cannot create return order");
    }

    returnOrder = orderRepository.save(returnOrder);

    List<OrderItem> originalItems = orderItemRepository.findByOrderId(originalOrderId);

    for (Map<String, Object> item : returnItems) {
      Long productId = Long.valueOf(item.get("product_id").toString());
      int qty = Integer.parseInt(item.get("quantity").toString());

      OrderItem originalItem = originalItems.stream()
          .filter(oi -> oi.getProductId().equals(productId))
          .findFirst()
          .orElseThrow(
              () -> new IllegalArgumentException("Product " + productId + " was not part of the original order"));

      if (qty > originalItem.getQuantity()) {
        throw new IllegalArgumentException("Cannot return more than originally purchased for product " + productId);
      }

      BigDecimal unitPrice = originalItem.getUnitPrice();
      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
      BigDecimal taxRate = originalItem.getTaxRate();
      BigDecimal itemTax = itemTotal.multiply(taxRate).divide(BigDecimal.valueOf(PERCENTAGE_BASE), 2,
          RoundingMode.HALF_UP);

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

    auditLogService.logAudit(AuditAction.CREATE_RETURN_ORDER, AuditResource.ORDER, returnOrder.getId(),
        String.format("Processed return for order #%d, Total Credit: %s", originalOrderId, returnTotal));

    return returnOrder;
  }

  public Page<Order> getOrders(Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    return Objects.requireNonNull(orderRepository.findAllByTenantId(tenantId, pageable));
  }

  public Page<Order> getOrdersByType(String type, Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    return Objects.requireNonNull(orderRepository.findByTenantIdAndType(tenantId, type, pageable));
  }

  public Map<String, Object> getOrderWithItems(Long id) {
    Long tenantId = TenantContext.getTenantId();
    Order order = orderRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    List<OrderItem> items = orderItemRepository.findByOrderIdAndTenantId(order.getId(), tenantId);
    return Objects.requireNonNull(Map.of("order", order, "items", items));
  }

  @Transactional(readOnly = true)
  public byte[] generateOrderPdf(Long id) {
    Long tenantId = TenantContext.getTenantId();
    Order order = orderRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    com.ims.model.Tenant tenant = tenantRepository
        .findById(tenantId)
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

    List<OrderItem> orderItems = orderItemRepository.findByOrderIdAndTenantId(order.getId(), tenantId);
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
  public Order confirmOrder(Long id, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    Order order = orderRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if (!"PENDING".equals(order.getStatus())) {
      throw new IllegalStateException("Only PENDING orders can be confirmed");
    }

    List<OrderItem> items = orderItemRepository.findByOrderIdAndTenantId(order.getId(), tenantId);

    if ("SALE".equals(order.getType())) {
      // Validate and reduce stock
      for (OrderItem item : items) {
        Product product = productRepository.findById(Objects.requireNonNull(item.getProductId()))
            .orElseThrow(() -> new EntityNotFoundException("Product not found: " + item.getProductId()));
        if (product.getStock() < item.getQuantity()) {
          throw new InsufficientStockException("Insufficient stock for " + product.getName(), product.getStock(),
              item.getQuantity());
        }
        stockService.stockOut(Objects.requireNonNull(item.getProductId()), item.getQuantity(),
            "Confirmed Sale Order #" + order.getId(), userId);
      }
      order.setStatus("CONFIRMED");
      // Auto-generate invoice for sales upon confirmation
      invoiceService.createFromOrder(order);
    } else if ("PURCHASE".equals(order.getType())) {
      order.setStatus("CONFIRMED");
    }

    order = orderRepository.save(order);
    auditLogService.logAudit(AuditAction.CONFIRM_ORDER, AuditResource.ORDER, id,
        "Confirmed " + order.getType() + " order #" + id);
    return order;
  }

  @Transactional
  public Order shipOrder(Long id, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    Order order = orderRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if (!"CONFIRMED".equals(order.getStatus())) {
      throw new IllegalStateException("Only CONFIRMED orders can be shipped");
    }

    order.setStatus("SHIPPED");
    order = orderRepository.save(order);
    auditLogService.logAudit(AuditAction.SHIP_ORDER, AuditResource.ORDER, id,
        "Shipped " + order.getType() + " order #" + id);
    return order;
  }

  @Transactional
  public Order completeOrder(Long id, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    Order order = orderRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if (!"SHIPPED".equals(order.getStatus()) && !"CONFIRMED".equals(order.getStatus())) {
      throw new IllegalStateException("Order must be SHIPPED or CONFIRMED to be completed");
    }

    if ("PURCHASE".equals(order.getType()) && !"RECEIVED".equals(order.getStatus())) {
      List<OrderItem> items = orderItemRepository.findByOrderIdAndTenantId(order.getId(), tenantId);
      for (OrderItem item : items) {
        stockService.stockIn(Objects.requireNonNull(item.getProductId()), item.getQuantity(),
            "Received Purchase Order #" + order.getId(), userId);
      }
      order.setStatus("RECEIVED");
    } else {
      order.setStatus("COMPLETED");
    }

    order = orderRepository.save(order);
    auditLogService.logAudit(AuditAction.COMPLETE_ORDER, AuditResource.ORDER, id,
        "Completed " + order.getType() + " order #" + id);
    return order;
  }

  @Transactional
  public Order cancelOrder(Long id, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    Order order = orderRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if ("COMPLETED".equals(order.getStatus()) || "RECEIVED".equals(order.getStatus())
        || "CANCELLED".equals(order.getStatus())) {
      throw new IllegalStateException("Cannot cancel an order that is already " + order.getStatus());
    }

    if ("SALE".equals(order.getType())
        && ("CONFIRMED".equals(order.getStatus()) || "SHIPPED".equals(order.getStatus()))) {
      List<OrderItem> items = orderItemRepository.findByOrderIdAndTenantId(order.getId(), tenantId);
      for (OrderItem item : items) {
        stockService.stockIn(Objects.requireNonNull(item.getProductId()), item.getQuantity(),
            "Cancelled Sale Order #" + order.getId(), userId);
      }
    }

    order.setStatus("CANCELLED");
    order = orderRepository.save(order);
    auditLogService.logAudit(AuditAction.CANCEL_ORDER, AuditResource.ORDER, id,
        "Cancelled " + order.getType() + " order #" + id);
    return order;
  }

  public Page<Order> getOrdersBySupplier(Long supplierId, Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    return Objects.requireNonNull(orderRepository.findByTenantIdAndSupplierId(tenantId, supplierId, pageable));
  }

  public Page<Order> getOrdersByCustomer(Long customerId, Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    return Objects.requireNonNull(orderRepository.findByTenantIdAndCustomerId(tenantId, customerId, pageable));
  }

  @Transactional
  public Order updateOrderStatus(Long id, String status) {
    Long tenantId = TenantContext.getTenantId();
    Order order = orderRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    order.setStatus(status);
    return Objects.requireNonNull(orderRepository.save(order));
  }
}
