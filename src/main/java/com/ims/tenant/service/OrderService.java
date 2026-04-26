package com.ims.tenant.service;

import com.ims.model.Order;
import com.ims.model.OrderItem;
import com.ims.product.Product;
import com.ims.model.OrderStatus;
import com.ims.product.ProductRepository;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditResource;
import com.ims.tenant.dto.OrderItemRequest;
import com.ims.tenant.dto.OrderRequest;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.SupplierRepository;
import com.ims.tenant.domain.pharmacy.PharmacyProduct;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util. HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.function.Function;
import java.time.LocalDate;
import java.util.stream.Collectors;
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
  private final com.ims.tenant.domain.pharmacy.PharmacyProductRepository pharmacyProductRepository;
  private final com.ims.platform.repository.TenantRepository tenantRepository;

  private static final int PERCENTAGE_BASE = 100;

  @Transactional
  public Order createPurchaseOrder(OrderRequest request, Long userId) {
    Long supplierId = request.getSupplierId();
    List<OrderItemRequest> itemRequests = request.getItems();

    // Validate supplier
    supplierRepository
        .findById(supplierId)
        .orElseThrow(() -> new EntityNotFoundException("Supplier not found"));

    // Bulk fetch and validate products
    Set<Long> productIds =
        itemRequests.stream().map(OrderItemRequest::getProductId).collect(Collectors.toSet());
    Map<Long, Product> productMap =
        productRepository.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

    if (productMap.size() < productIds.size()) {
      Set<Long> missingIds = new HashSet<>(productIds);
      missingIds.removeAll(productMap.keySet());
      throw new EntityNotFoundException("Products not found: " + missingIds);
    }

    BigDecimal totalAmount = BigDecimal.ZERO;
    BigDecimal taxAmount = BigDecimal.ZERO;
    List<OrderItem> orderItems = new ArrayList<>();

    for (OrderItemRequest item : itemRequests) {
      BigDecimal unitPrice = item.getUnitPrice();
      int qty = item.getQuantity();
      BigDecimal discount = item.getDiscount() != null ? item.getDiscount() : BigDecimal.ZERO;
      BigDecimal taxRate = item.getTaxRate() != null ? item.getTaxRate() : BigDecimal.ZERO;

      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount);
      BigDecimal itemTax =
          itemTotal
              .multiply(taxRate)
              .divide(BigDecimal.valueOf(PERCENTAGE_BASE), 2, RoundingMode.HALF_UP);

      totalAmount = totalAmount.add(itemTotal);
      taxAmount = taxAmount.add(itemTax);

      orderItems.add(
          OrderItem.builder()
              .productId(item.getProductId())
              .quantity(qty)
              .unitPrice(unitPrice)
              .discount(discount)
              .taxRate(taxRate)
              .total(itemTotal)
              .build());
    }

    Order order =
        Order.builder()
            .type("PURCHASE")
            .status(com.ims.model.OrderStatus.PENDING)
            .supplierId(supplierId)
            .totalAmount(totalAmount)
            .taxAmount(taxAmount)
            .notes(request.getNotes())
            .createdBy(userId)
            .build();

    Order savedPurchaseOrder = orderRepository.save(order);
    final Long orderId = savedPurchaseOrder.getId();
    orderItems.forEach(oi -> oi.setOrderId(orderId));
    orderItemRepository.saveAll(orderItems);

    log.info("Purchase order created: id={} total={}", orderId, totalAmount);
    auditLogService.logAudit(
        AuditAction.CREATE_PURCHASE_ORDER,
        AuditResource.ORDER,
        orderId,
        String.format(
            "Created purchase order #%d, Supplier: %d, Total: %s",
            orderId, supplierId, totalAmount));

    return savedPurchaseOrder;
  }

  @Transactional
  public Order createSalesOrder(OrderRequest request, Long userId) {
    Long customerId = request.getCustomerId();
    List<OrderItemRequest> itemRequests = request.getItems();

    if (customerId != null) {
      customerRepository
          .findById(customerId)
          .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
    }

    // Bulk fetch products and pharmacy details
    Set<Long> productIds =
        itemRequests.stream().map(OrderItemRequest::getProductId).collect(Collectors.toSet());
    Map<Long, Product> productMap =
        productRepository.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

    if (productMap.size() < productIds.size()) {
      Set<Long> missingIds = new HashSet<>(productIds);
      missingIds.removeAll(productMap.keySet());
      throw new EntityNotFoundException("Products not found: " + missingIds);
    }

    Map<Long, PharmacyProduct> pharmacyProductMap =
        pharmacyProductRepository.findAllById(productIds).stream()
            .collect(
                Collectors.toMap(pp -> pp.getProduct().getId(), Function.identity()));

    BigDecimal totalAmount = BigDecimal.ZERO;
    BigDecimal taxAmount = BigDecimal.ZERO;
    List<OrderItem> orderItems = new ArrayList<>();

    for (OrderItemRequest item : itemRequests) {
      Long productId = item.getProductId();
      Product product = productMap.get(productId);
      int qty = item.getQuantity();

      // PHARMACY EXPIRY CHECK
      PharmacyProduct pp = pharmacyProductMap.get(productId);
      if (pp != null && pp.getExpiryDate() != null && pp.getExpiryDate().isBefore(LocalDate.now())) {
        throw new IllegalArgumentException(
            "Product " + product.getName() + " has expired on " + pp.getExpiryDate());
      }

      BigDecimal unitPrice = item.getUnitPrice();
      BigDecimal discount = item.getDiscount() != null ? item.getDiscount() : BigDecimal.ZERO;
      BigDecimal taxRate = item.getTaxRate() != null ? item.getTaxRate() : BigDecimal.ZERO;

      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount);
      BigDecimal itemTax =
          itemTotal
              .multiply(taxRate)
              .divide(BigDecimal.valueOf(PERCENTAGE_BASE), 2, RoundingMode.HALF_UP);

      totalAmount = totalAmount.add(itemTotal);
      taxAmount = taxAmount.add(itemTax);

      orderItems.add(
          OrderItem.builder()
              .productId(productId)
              .quantity(qty)
              .unitPrice(unitPrice)
              .discount(discount)
              .taxRate(taxRate)
              .total(itemTotal)
              .build());
    }

    BigDecimal rootDiscount =
        request.getDiscountTotal() != null ? request.getDiscountTotal() : BigDecimal.ZERO;
    BigDecimal grandTotalCalculated = totalAmount.add(taxAmount).subtract(rootDiscount);

    if (request.getGrandTotal() != null
        && grandTotalCalculated.compareTo(request.getGrandTotal()) != 0) {
      throw new IllegalArgumentException(
          "Grand total mismatch. Calculated: " + grandTotalCalculated);
    }

    Order salesOrder =
        Order.builder()
            .type("SALE")
            .status(OrderStatus.PENDING)
            .customerId(customerId)
            .totalAmount(totalAmount)
            .taxAmount(taxAmount)
            .discount(rootDiscount)
            .notes(request.getNotes())
            .createdBy(userId)
            .build();
    Order savedSalesOrder = orderRepository.save(salesOrder);

    final Long orderId = savedSalesOrder.getId();
    orderItems.forEach(oi -> oi.setOrderId(orderId));
    orderItemRepository.saveAll(orderItems);

    log.info("Sales order created: id={} total={}", orderId, totalAmount);

    auditLogService.logAudit(
        AuditAction.CREATE_SALE_ORDER,
        AuditResource.ORDER,
        orderId,
        String.format(
            "Created sales order #%d, Customer: %d, Total: %s", orderId, customerId, totalAmount));

    return savedSalesOrder;
  }

  @Transactional
  public Order createReturnOrder(OrderRequest request, Long userId) {
    Long originalOrderId = request.getOriginalOrderId();
    List<OrderItemRequest> returnItems = request.getItems();

    Order originalOrder =
        orderRepository
            .lockById(originalOrderId)
            .orElseThrow(() -> new EntityNotFoundException("Original order not found"));

    if (!"SALE".equals(originalOrder.getType())) {
      throw new IllegalArgumentException("Returns can only be created for SALE orders");
    }

    BigDecimal returnTotal = BigDecimal.ZERO;
    BigDecimal returnTax = BigDecimal.ZERO;

    Order initialReturnOrder =
        Order.builder()
            .type("RETURN")
            .status(com.ims.model.OrderStatus.COMPLETED)
            .customerId(originalOrder.getCustomerId())
            .referenceOrderId(originalOrderId)
            .notes(request.getNotes() != null ? request.getNotes() : "Customer return")
            .createdBy(userId)
            .build();

    Order savedReturnOrder = orderRepository.save(initialReturnOrder);

    List<OrderItem> originalItems = orderItemRepository.findByOrderId(originalOrderId);
    List<OrderItem> returnOrderItems = new ArrayList<>();

    // 1. Bulk fetch all prior return items for this order
    List<Order> priorReturnOrders = orderRepository.findByReferenceOrderId(originalOrderId);
    Map<Long, Integer> alreadyReturnedMap = new HashMap<>();

    if (!priorReturnOrders.isEmpty()) {
      List<Long> priorReturnIds =
          priorReturnOrders.stream().map(Order::getId).collect(Collectors.toList());
      orderItemRepository.findByOrderIdIn(priorReturnIds).forEach(oi -> {
        alreadyReturnedMap.merge(oi.getProductId(), oi.getQuantity(), (a, b) -> a + b);
      });
    }

    for (OrderItemRequest item : returnItems) {
      Long productId = item.getProductId();
      int qty = item.getQuantity();

      OrderItem originalItem =
          originalItems.stream()
              .filter(oi -> oi.getProductId().equals(productId))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Product " + productId + " was not part of the original order"));

      int alreadyReturned = alreadyReturnedMap.getOrDefault(productId, 0);

      if (qty + alreadyReturned > originalItem.getQuantity()) {
        throw new IllegalArgumentException(
            String.format(
                "Cannot return %d units of product %d. Already returned: %d, Purchased: %d",
                qty, productId, alreadyReturned, originalItem.getQuantity()));
      }

      BigDecimal unitPrice = originalItem.getUnitPrice();
      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
      BigDecimal taxRate = originalItem.getTaxRate();
      BigDecimal itemTax =
          itemTotal
              .multiply(taxRate)
              .divide(BigDecimal.valueOf(PERCENTAGE_BASE), 2, RoundingMode.HALF_UP);

      returnTotal = returnTotal.add(itemTotal);
      returnTax = returnTax.add(itemTax);

      returnOrderItems.add(
          OrderItem.builder()
              .productId(productId)
              .quantity(qty)
              .unitPrice(unitPrice)
              .taxRate(taxRate)
              .total(itemTotal)
              .build());

      // Restore stock
      stockService.stockIn(
          productId,
          qty,
          "Return for Order #" + originalOrderId,
          userId);
    }

    final Long returnOrderId = savedReturnOrder.getId();
    returnOrderItems.forEach(oi -> oi.setOrderId(returnOrderId));
    orderItemRepository.saveAll(returnOrderItems);

    savedReturnOrder.setTotalAmount(returnTotal);
    savedReturnOrder.setTaxAmount(returnTax);
    savedReturnOrder = orderRepository.save(savedReturnOrder);

    // Create Credit Note
    invoiceService.createCreditNote(savedReturnOrder, null);

    auditLogService.logAudit(
        AuditAction.CREATE_RETURN_ORDER,
        AuditResource.ORDER,
        returnOrderId,
        String.format(
            "Processed return for order #%d, Total Credit: %s", originalOrderId, returnTotal));

    return savedReturnOrder;
  }

  public Page<Order> getOrders(Pageable pageable) {
    return orderRepository.findAll(pageable);
  }

  public Page<Order> getOrdersByType(String type, Pageable pageable) {
    return orderRepository.findByType(type, pageable);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getOrderWithItems(Long id) {
    Order order =
        orderRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
    return Map.of("order", order, "items", items);
  }

  @Transactional(readOnly = true)
  public byte[] generateOrderPdf(Long id) {
    Order order =
        orderRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    com.ims.model.Tenant tenant =
        tenantRepository
            .findById(com.ims.shared.auth.TenantContext.getTenantId())
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    String partnerName = "N/A";
    String partnerAddress = "N/A";

    if ("SALE".equals(order.getType()) && order.getCustomerId() != null) {
      var customer =
          customerRepository.findById(order.getCustomerId()).orElse(null);
      if (customer != null) {
        partnerName = customer.getName();
        partnerAddress = customer.getAddress();
      }
    } else if ("PURCHASE".equals(order.getType()) && order.getSupplierId() != null) {
      var supplier =
          supplierRepository.findById(order.getSupplierId()).orElse(null);
      if (supplier != null) {
        partnerName = supplier.getName();
        partnerAddress = supplier.getAddress();
      }
    }

    List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

    // Batch fetch products to avoid N+1
    List<Long> productIds =
        orderItems.stream()
            .map(OrderItem::getProductId)
            .collect(Collectors.toList());
    Map<Long, String> productNames =
        productRepository.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Product::getName));

    List<Map<String, Object>> items =
        orderItems.stream()
            .map(
                item -> {
                  String productName = productNames.getOrDefault(item.getProductId(), "Unknown");
                  Map<String, Object> map = new HashMap<>();
                  map.put("productName", productName);
                  map.put("quantity", item.getQuantity());
                  map.put("unitPrice", item.getUnitPrice());
                  map.put("discount", item.getDiscount());
                  map.put("total", item.getTotal());
                  return map;
                })
            .collect(Collectors.toList());

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
    context.setVariable(
        "subtotal", order.getTotalAmount().subtract(order.getTaxAmount()).add(order.getDiscount()));
    context.setVariable("taxAmount", order.getTaxAmount());
    context.setVariable("discount", order.getDiscount());
    context.setVariable("totalAmount", order.getTotalAmount());

    return pdfService.generatePdfFromHtml("order-summary", context);
  }

  private static final Map<OrderStatus, Set<com.ims.model.OrderStatus>>
      ALLOWED_TRANSITIONS =
          Map.of(
              OrderStatus.PENDING,
                  Set.of(
                      OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
              OrderStatus.CONFIRMED,
                  Set.of(
                      OrderStatus.SHIPPED,
                      OrderStatus.CANCELLED,
                      OrderStatus.COMPLETED,
                      OrderStatus.RECEIVED),
              OrderStatus.SHIPPED,
                  Set.of(
                      OrderStatus.COMPLETED, OrderStatus.RECEIVED),
              OrderStatus.COMPLETED, Set.of(),
              OrderStatus.RECEIVED, Set.of(),
              OrderStatus.CANCELLED, Set.of());

  private void validateTransition(Order order, OrderStatus newStatus) {
    if (!ALLOWED_TRANSITIONS.get(order.getStatus()).contains(newStatus)) {
      throw new IllegalStateException(
          "Invalid transition: " + order.getStatus() + " -> " + newStatus);
    }
  }

  @Transactional
  public Order confirmOrder(Long id, Long userId) {
    Order order =
        orderRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    validateTransition(order, com.ims.model.OrderStatus.CONFIRMED);

    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

    if ("SALE".equals(order.getType())) {
      // Validate and reduce stock
      for (OrderItem item : items) {
        // STOCK CHECK REMOVED: stockService.stockOut handles pessimistic lock and insufficient
        // stock check atomically.
        stockService.stockOut(
            item.getProductId(),
            item.getQuantity(),
            "Confirmed Sale Order #" + order.getId(),
            userId);
      }
      order.setStatus(com.ims.model.OrderStatus.CONFIRMED);
      // Auto-generate invoice for sales upon confirmation
      invoiceService.createFromOrder(order);
    } else if ("PURCHASE".equals(order.getType())) {
      order.setStatus(com.ims.model.OrderStatus.CONFIRMED);
    }

    order = orderRepository.save(order);
    auditLogService.logAudit(
        AuditAction.CONFIRM_ORDER,
        AuditResource.ORDER,
        id,
        "Confirmed " + order.getType() + " order #" + id);
    return order;
  }

  @Transactional
  public Order shipOrder(Long id, Long userId) {
    Order order =
        orderRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    validateTransition(order, com.ims.model.OrderStatus.SHIPPED);

    order.setStatus(com.ims.model.OrderStatus.SHIPPED);
    order = orderRepository.save(order);
    auditLogService.logAudit(
        AuditAction.SHIP_ORDER,
        AuditResource.ORDER,
        id,
        "Shipped " + order.getType() + " order #" + id);
    return order;
  }

  @Transactional
  public Order completeOrder(Long id, Long userId) {
    Order order =
        orderRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if ("PURCHASE".equals(order.getType())) {
      validateTransition(order, com.ims.model.OrderStatus.RECEIVED);
      // For purchase, completion means receiving goods
      List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
      for (OrderItem item : items) {
        stockService.stockIn(
            item.getProductId(),
            item.getQuantity(),
            "Received Purchase Order #" + order.getId(),
            userId);
      }
      order.setStatus(com.ims.model.OrderStatus.RECEIVED);
    } else {
      validateTransition(order, com.ims.model.OrderStatus.COMPLETED);
      order.setStatus(com.ims.model.OrderStatus.COMPLETED);
    }

    order = orderRepository.save(order);
    auditLogService.logAudit(
        AuditAction.COMPLETE_ORDER,
        AuditResource.ORDER,
        id,
        "Completed " + order.getType() + " order #" + id);
    return order;
  }

  @Transactional
  public Order cancelOrder(Long id, Long userId) {
    Order order =
        orderRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    validateTransition(order, com.ims.model.OrderStatus.CANCELLED);

    // If it was CONFIRMED or SHIPPED, we might need to revert stock for SALES
    if ("SALE".equals(order.getType())
        && (order.getStatus() == com.ims.model.OrderStatus.CONFIRMED
            || order.getStatus() == com.ims.model.OrderStatus.SHIPPED)) {
      List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
      for (OrderItem item : items) {
        stockService.stockIn(
            item.getProductId(),
            item.getQuantity(),
            "Cancelled Sale Order #" + order.getId(),
            userId);
      }
    }

    order.setStatus(com.ims.model.OrderStatus.CANCELLED);
    order = orderRepository.save(order);
    auditLogService.logAudit(
        AuditAction.CANCEL_ORDER,
        AuditResource.ORDER,
        id,
        "Cancelled " + order.getType() + " order #" + id);
    return order;
  }

  public Page<Order> getOrdersBySupplier(Long supplierId, Pageable pageable) {
    return orderRepository.findBySupplierId(supplierId, pageable);
  }

  public Page<Order> getOrdersByCustomer(Long customerId, Pageable pageable) {
    return orderRepository.findByCustomerId(customerId, pageable);
  }
}
