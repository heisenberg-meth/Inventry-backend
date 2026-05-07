package com.ims.tenant.service;

import com.ims.model.Order;
import com.ims.model.OrderItem;
import com.ims.order.entity.OrderStatus;
import com.ims.order.entity.OrderType;
import com.ims.product.Product;
import com.ims.product.ProductRepository;
import com.ims.product.ProductService;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditResource;
import com.ims.shared.exception.InsufficientStockException;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.OrderRepository;
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
  private final ProductService productService;
  private final SupplierRepository supplierRepository;
  private final CustomerRepository customerRepository;
  private final InventoryService inventoryService;
  private final InvoiceService invoiceService;
  private final com.ims.shared.audit.AuditLogService auditLogService;
  private final com.ims.shared.pdf.PdfService pdfService;
  private final com.ims.platform.repository.TenantRepository tenantRepository;
  private final com.ims.shared.outbox.OutboxService outboxService;
  private final com.ims.shared.metrics.BusinessMetricsService businessMetricsService;

  private static final int PERCENTAGE_BASE = 100;

  /**
   * Phase 5.2.6: PURCHASE ORDER FLOW
   */
  @Transactional
  public com.ims.order.dto.OrderResponse createPurchaseOrder(Long tenantId,
      com.ims.order.dto.CreateOrderRequest request,
      Long userId) {
    Long supplierId = request.getSupplierId();

    var supplierOpt = supplierRepository.findActiveByIdAndTenantId(supplierId, tenantId);
    if (supplierOpt.isEmpty()) {
      throw new EntityNotFoundException("Supplier not found or does not belong to your tenant");
    }

    BigDecimal totalAmount = BigDecimal.ZERO;
    BigDecimal taxAmount = BigDecimal.ZERO;

    for (com.ims.order.dto.OrderItemRequest itemReq : request.getItems()) {
      Long productId = itemReq.getProductId();
      int qty = itemReq.getQuantity();

      Product product = productService.findByIdWithLock(productId)
          .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));

      BigDecimal unitPrice = itemReq.getUnitPrice() != null ? itemReq.getUnitPrice()
          : (product.getPurchasePrice() != null ? product.getPurchasePrice() : BigDecimal.ZERO);

      BigDecimal discount = itemReq.getDiscount() != null ? itemReq.getDiscount() : BigDecimal.ZERO;
      BigDecimal taxRate = itemReq.getTaxRate() != null ? itemReq.getTaxRate() : BigDecimal.ZERO;

      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount);
      BigDecimal itemTax = itemTotal.multiply(taxRate).divide(BigDecimal.valueOf(PERCENTAGE_BASE), 2,
          RoundingMode.HALF_UP);

      totalAmount = totalAmount.add(itemTotal);
      taxAmount = taxAmount.add(itemTax);
    }

    Order order = Order.builder()
        .type(OrderType.PURCHASE)
        .status(OrderStatus.PENDING)
        .tenantId(tenantId)
        .supplierId(supplierId)
        .totalAmount(totalAmount)
        .taxAmount(taxAmount)
        .notes(request.getNotes())
        .createdBy(userId)
        .build();

    order = orderRepository.save(order);

    for (com.ims.order.dto.OrderItemRequest itemReq : request.getItems()) {
      Long productId = itemReq.getProductId();
      int qty = itemReq.getQuantity();
      Product product = productRepository.findById(productId).orElseThrow();

      BigDecimal unitPrice = itemReq.getUnitPrice() != null ? itemReq.getUnitPrice()
          : (product.getPurchasePrice() != null ? product.getPurchasePrice() : BigDecimal.ZERO);
      BigDecimal discount = itemReq.getDiscount() != null ? itemReq.getDiscount() : BigDecimal.ZERO;
      BigDecimal taxRate = itemReq.getTaxRate() != null ? itemReq.getTaxRate() : BigDecimal.ZERO;
      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount);

      OrderItem orderItem = OrderItem.builder()
          .tenantId(tenantId)
          .orderId(order.getId())
          .productId(productId)
          .quantity(qty)
          .unitPrice(unitPrice)
          .discount(discount)
          .taxRate(taxRate)
          .subtotal(itemTotal)
          .total(itemTotal)
          .build();
      orderItemRepository.save(orderItem);
    }

    log.info("Purchase order created: id={} total={}", order.getId(), totalAmount);
    auditLogService.logAudit(AuditAction.CREATE_PURCHASE_ORDER, AuditResource.ORDER, order.getId(),
        "Created purchase order #" + order.getId());

    businessMetricsService.incrementOrdersCreated();
    outboxService.saveEvent("ORDER", order.getId().toString(), "CREATED", order, tenantId);

    return toOrderResponse(order);
  }

  /**
   * Phase 5.2.1: SALE ORDER FLOW
   */
  @Transactional
  public com.ims.order.dto.OrderResponse createSalesOrder(Long tenantId, com.ims.order.dto.CreateOrderRequest request,
      Long userId) {
    Long customerId = request.getCustomerId();

    if (customerId != null) {
      customerRepository.findByIdAndTenantId(customerId, tenantId)
          .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
    }

    BigDecimal totalAmount = BigDecimal.ZERO;
    BigDecimal taxAmount = BigDecimal.ZERO;

    for (com.ims.order.dto.OrderItemRequest itemReq : request.getItems()) {
      Long productId = itemReq.getProductId();
      int qty = itemReq.getQuantity();

      Product product = productService.findByIdWithLock(productId)
          .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));

      int availableStock = inventoryService.getAvailableStock(tenantId, productId);
      if (availableStock < qty) {
        throw new InsufficientStockException("Insufficient stock for " + product.getName(), availableStock, qty);
      }

      BigDecimal unitPrice = itemReq.getUnitPrice() != null ? itemReq.getUnitPrice() : product.getSalePrice();
      BigDecimal discount = itemReq.getDiscount() != null ? itemReq.getDiscount() : BigDecimal.ZERO;
      BigDecimal taxRate = itemReq.getTaxRate() != null ? itemReq.getTaxRate() : BigDecimal.ZERO;

      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount);
      BigDecimal itemTax = itemTotal.multiply(taxRate).divide(BigDecimal.valueOf(PERCENTAGE_BASE), 2,
          RoundingMode.HALF_UP);

      totalAmount = totalAmount.add(itemTotal);
      taxAmount = taxAmount.add(itemTax);
    }

    BigDecimal rootDiscount = request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO;

    Order order = Order.builder()
        .type(OrderType.SALE)
        .status(OrderStatus.PENDING)
        .tenantId(tenantId)
        .customerId(customerId)
        .totalAmount(totalAmount)
        .taxAmount(taxAmount)
        .discount(rootDiscount)
        .notes(request.getNotes())
        .createdBy(userId)
        .build();

    order = orderRepository.save(order);

    for (com.ims.order.dto.OrderItemRequest itemReq : request.getItems()) {
      Long productId = itemReq.getProductId();
      int qty = itemReq.getQuantity();
      Product product = productRepository.findById(productId).orElseThrow();

      BigDecimal unitPrice = itemReq.getUnitPrice() != null ? itemReq.getUnitPrice() : product.getSalePrice();
      BigDecimal discount = itemReq.getDiscount() != null ? itemReq.getDiscount() : BigDecimal.ZERO;
      BigDecimal taxRate = itemReq.getTaxRate() != null ? itemReq.getTaxRate() : BigDecimal.ZERO;
      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount);

      OrderItem orderItem = OrderItem.builder()
          .tenantId(tenantId)
          .orderId(order.getId())
          .productId(productId)
          .quantity(qty)
          .unitPrice(unitPrice)
          .discount(discount)
          .taxRate(taxRate)
          .subtotal(itemTotal)
          .total(itemTotal)
          .build();
      orderItemRepository.save(orderItem);
    }

    log.info("Sales order created: id={} total={}", order.getId(), totalAmount);
    auditLogService.logAudit(AuditAction.CREATE_SALE_ORDER, AuditResource.ORDER, order.getId(),
        "Created sales order #" + order.getId());

    businessMetricsService.incrementOrdersCreated();
    outboxService.saveEvent("ORDER", order.getId().toString(), "CREATED", order, tenantId);

    return toOrderResponse(order);
  }

  @Transactional
  public Order createReturnOrder(Long tenantId, Map<String, Object> request, Long userId) {
    Long originalOrderId = Long.valueOf(request.get("original_order_id").toString());
    Object rawItems = request.get("items");
    if (!(rawItems instanceof List<?> list)) {
      throw new IllegalArgumentException("Invalid items format");
    }

    Order originalOrder = orderRepository.findByIdAndTenantId(originalOrderId, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Original order not found"));

    if (OrderType.SALE != originalOrder.getType()) {
      throw new IllegalArgumentException("Returns can only be created for SALE orders");
    }

    BigDecimal returnTotal = BigDecimal.ZERO;
    BigDecimal returnTax = BigDecimal.ZERO;

    Order returnOrder = Order.builder()
        .type(OrderType.RETURN)
        .status(OrderStatus.COMPLETED)
        .tenantId(tenantId)
        .customerId(originalOrder.getCustomerId())
        .referenceOrderId(originalOrderId)
        .notes(request.getOrDefault("notes", "Customer return").toString())
        .createdBy(userId)
        .build();

    returnOrder = orderRepository.save(returnOrder);

    List<OrderItem> originalItems = orderItemRepository.findByOrderIdAndTenantId(originalOrderId, tenantId);

    for (Object itemObj : list) {
      @SuppressWarnings("unchecked")
      Map<String, Object> item = (Map<String, Object>) itemObj;
      Long productId = Long.valueOf(item.get("product_id").toString());
      int qty = Integer.parseInt(item.get("quantity").toString());

      OrderItem originalItem = originalItems.stream()
          .filter(oi -> oi.getProductId().equals(productId))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Product not in original order"));

      int alreadyReturned = orderItemRepository.sumReturnedQtyByTenantId(originalOrderId, tenantId, productId);
      if (alreadyReturned + qty > originalItem.getQuantity()) {
        throw new IllegalArgumentException("Return quantity too high");
      }

      BigDecimal unitPrice = originalItem.getUnitPrice();
      BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
      BigDecimal taxRate = originalItem.getTaxRate();
      BigDecimal itemTax = itemTotal.multiply(taxRate).divide(BigDecimal.valueOf(PERCENTAGE_BASE), 2,
          RoundingMode.HALF_UP);

      returnTotal = returnTotal.add(itemTotal);
      returnTax = returnTax.add(itemTax);

      OrderItem returnOrderItem = OrderItem.builder()
          .tenantId(tenantId)
          .orderId(returnOrder.getId())
          .productId(productId)
          .quantity(qty)
          .unitPrice(unitPrice)
          .taxRate(taxRate)
          .subtotal(itemTotal)
          .total(itemTotal)
          .build();
      orderItemRepository.save(returnOrderItem);

      inventoryService.increaseStock(tenantId, productId, qty, "Return Order #" + originalOrderId,
          userId);
    }

    returnOrder.setTotalAmount(returnTotal);
    returnOrder.setTaxAmount(returnTax);
    orderRepository.save(returnOrder);

    invoiceService.createCreditNote(returnOrder, null);
    outboxService.saveEvent("ORDER", returnOrder.getId().toString(), "RETURNED", returnOrder, tenantId);

    return returnOrder;
  }

  private com.ims.order.dto.OrderResponse toOrderResponse(Order order) {
    List<OrderItem> items = orderItemRepository.findByOrderIdAndTenantId(order.getId(), order.getTenantId());
    List<com.ims.order.dto.OrderItemResponse> itemResponses = items.stream()
        .map(this::toOrderItemResponse)
        .toList();

    return com.ims.order.dto.OrderResponse.builder()
        .id(order.getId())
        .type(order.getType())
        .status(order.getStatus())
        .customerId(order.getCustomerId())
        .supplierId(order.getSupplierId())
        .totalAmount(order.getTotalAmount())
        .taxAmount(order.getTaxAmount())
        .discount(order.getDiscount())
        .notes(order.getNotes())
        .createdBy(order.getCreatedBy())
        .createdAt(order.getCreatedAt())
        .items(itemResponses)
        .build();
  }

  private com.ims.order.dto.OrderItemResponse toOrderItemResponse(OrderItem item) {
    Product product = productRepository.findById(item.getProductId()).orElse(null);
    return com.ims.order.dto.OrderItemResponse.builder()
        .id(item.getId())
        .productId(item.getProductId())
        .productName(product != null ? product.getName() : "Unknown")
        .sku(product != null ? product.getSku() : null)
        .quantity(item.getQuantity())
        .unitPrice(item.getUnitPrice())
        .discount(item.getDiscount())
        .taxRate(item.getTaxRate())
        .subtotal(item.getTotal())
        .build();
  }

  public Page<Order> getOrders(Long tenantId, Pageable pageable) {
    return orderRepository.findAllByTenantId(tenantId, pageable);
  }

  public Page<Order> getOrdersByType(Long tenantId, OrderType type, Pageable pageable) {
    return orderRepository.findByTenantIdAndType(tenantId, type, pageable);
  }

  public com.ims.order.dto.OrderResponse getOrderWithItems(Long id, Long tenantId) {
    Order order = orderRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    return toOrderResponse(order);
  }

  @Transactional(readOnly = true)
  public byte[] generateOrderPdf(Long id, Long tenantId) {
    Order order = orderRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    com.ims.model.Tenant tenant = tenantRepository.findById(tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

    String partnerName = "N/A";
    String partnerAddress = "N/A";

    if (OrderType.SALE == order.getType() && order.getCustomerId() != null) {
      var customer = customerRepository.findById(order.getCustomerId()).orElse(null);
      if (customer != null) {
        partnerName = customer.getName();
        partnerAddress = customer.getAddress();
      }
    } else if (OrderType.PURCHASE == order.getType() && order.getSupplierId() != null) {
      var supplier = supplierRepository.findById(order.getSupplierId()).orElse(null);
      if (supplier != null) {
        partnerName = supplier.getName();
        partnerAddress = supplier.getAddress();
      }
    }

    List<OrderItem> orderItems = orderItemRepository.findByOrderIdAndTenantId(order.getId(), tenantId);
    List<Long> productIds = orderItems.stream().map(OrderItem::getProductId).distinct().toList();
    Map<Long, String> productNameMap = productRepository.findAllById(productIds).stream()
        .collect(java.util.stream.Collectors.toMap(Product::getId, Product::getName));

    List<Map<String, Object>> items = orderItems.stream().map(item -> {
      Map<String, Object> map = new java.util.HashMap<>();
      map.put("productName", productNameMap.getOrDefault(item.getProductId(), "Unknown"));
      map.put("quantity", item.getQuantity());
      map.put("unitPrice", item.getUnitPrice());
      map.put("discount", item.getDiscount());
      map.put("total", item.getTotal());
      return map;
    }).toList();

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
  public Order confirmOrder(Long id, Long tenantId, Long userId) {
    Order order = orderRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if (OrderStatus.PENDING != order.getStatus()) {
      throw new IllegalStateException("Only PENDING orders can be confirmed");
    }

    if (OrderType.SALE == order.getType()) {
      List<OrderItem> items = orderItemRepository.findByOrderIdAndTenantId(order.getId(), order.getTenantId());
      for (OrderItem item : items) {
        productService.findByIdWithLock(item.getProductId())
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        int availableStock = inventoryService.getAvailableStock(order.getTenantId(), item.getProductId());
        if (availableStock < item.getQuantity()) {
          throw new InsufficientStockException("Insufficient stock", availableStock, item.getQuantity());
        }
        inventoryService.decreaseStock(order.getTenantId(), item.getProductId(), item.getQuantity(),
            "Confirmed Sale Order #" + order.getId(), userId);
      }
      order.setStatus(OrderStatus.CONFIRMED);
      invoiceService.createFromOrder(order);
    } else {
      order.setStatus(OrderStatus.CONFIRMED);
    }

    order = orderRepository.save(order);
    outboxService.saveEvent("ORDER", order.getId().toString(), "CONFIRMED", order, tenantId);
    return order;
  }

  @Transactional
  public Order shipOrder(Long id, Long tenantId, Long userId) {
    Order order = orderRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    order.setStatus(OrderStatus.SHIPPED);
    order = orderRepository.save(order);
    outboxService.saveEvent("ORDER", order.getId().toString(), "SHIPPED", order, tenantId);
    return order;
  }

  @Transactional
  public Order completeOrder(Long id, Long tenantId, Long userId) {
    Order order = orderRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if (OrderType.PURCHASE == order.getType() && OrderStatus.RECEIVED != order.getStatus()) {
      List<OrderItem> items = orderItemRepository.findByOrderIdAndTenantId(order.getId(), order.getTenantId());
      for (OrderItem item : items) {
        inventoryService.increaseStock(order.getTenantId(), item.getProductId(), item.getQuantity(),
            "Received Purchase Order #" + order.getId(), userId);
      }
      order.setStatus(OrderStatus.RECEIVED);
    } else {
      order.setStatus(OrderStatus.COMPLETED);
    }

    order = orderRepository.save(order);
    outboxService.saveEvent("ORDER", order.getId().toString(), "COMPLETED", order, tenantId);
    return order;
  }

  @Transactional
  public Order cancelOrder(Long id, Long tenantId, Long userId) {
    Order order = orderRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));

    if (OrderType.SALE == order.getType()
        && (OrderStatus.CONFIRMED == order.getStatus() || OrderStatus.SHIPPED == order.getStatus())) {
      List<OrderItem> items = orderItemRepository.findByOrderIdAndTenantId(order.getId(), order.getTenantId());
      for (OrderItem item : items) {
        inventoryService.increaseStock(order.getTenantId(), item.getProductId(), item.getQuantity(),
            "Cancelled Sale Order #" + order.getId(), userId);
      }
    }

    order.setStatus(OrderStatus.CANCELLED);
    order = orderRepository.save(order);
    outboxService.saveEvent("ORDER", order.getId().toString(), "CANCELLED", order, tenantId);
    return order;
  }

  public Page<Order> getOrdersBySupplier(Long tenantId, Long supplierId, Pageable pageable) {
    return orderRepository.findByTenantIdAndSupplierId(tenantId, supplierId, pageable);
  }

  public Page<Order> getOrdersByCustomer(Long tenantId, Long customerId, Pageable pageable) {
    return orderRepository.findByTenantIdAndCustomerId(tenantId, customerId, pageable);
  }

  @Transactional
  public Order updateOrderStatus(Long id, Long tenantId, OrderStatus status) {
    Order order = orderRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    order.setStatus(status);
    return orderRepository.save(order);
  }
}
