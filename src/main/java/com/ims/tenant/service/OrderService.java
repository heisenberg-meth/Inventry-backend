package com.ims.tenant.service;

import com.ims.model.Invoice;
import com.ims.model.Order;
import com.ims.model.OrderItem;
import com.ims.model.Product;
import com.ims.shared.auth.TenantContext;
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
 
  private static final int PERCENTAGE_BASE = 100;

  @Transactional
  public Map<String, Object> createPurchaseOrder(
      Long tenantId, Map<String, Object> request, Long userId) {
    Long supplierId = Long.valueOf(request.get("supplier_id").toString());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) request.get("items");

    // Validate supplier belongs to tenant
    supplierRepository
        .findByIdAndTenantId(supplierId, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Supplier not found"));

    BigDecimal totalAmount = BigDecimal.ZERO;
    BigDecimal taxAmount = BigDecimal.ZERO;

    // Save order
    Order order =
        Order.builder()
            .tenantId(tenantId)
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
      stockService.stockIn(tenantId, productId, qty, "Purchase Order #" + order.getId(), userId);
    }

    log.info(
        "Purchase order created: id={} tenant={} total={}", order.getId(), tenantId, totalAmount);
    return Map.of("order_id", order.getId(), "total", totalAmount);
  }

  @Transactional
  public Map<String, Object> createSalesOrder(
      Long tenantId, Map<String, Object> request, Long userId) {
    Long customerId =
        request.containsKey("customer_id")
            ? Long.valueOf(request.get("customer_id").toString())
            : null;
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) request.get("items");
    // Validate customer if provided
    if (customerId != null) {
      customerRepository
          .findByIdAndTenantId(customerId, tenantId)
          .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
    }

    // CHECK ALL stock availability BEFORE processing any items
    for (Map<String, Object> item : items) {
      Long productId = Long.valueOf(item.get("product_id").toString());
      int qty = Integer.parseInt(item.get("quantity").toString());

      Product product =
          productRepository
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

    Order order =
        Order.builder()
            .tenantId(tenantId)
            .type("SALE")
            .status("COMPLETED")
            .customerId(customerId)
            .totalAmount(totalAmount)
            .taxAmount(taxAmount)
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
      stockService.stockOut(tenantId, productId, qty, "Sale Order #" + order.getId(), userId);
    }

    // Auto-generate invoice
    Invoice invoice = invoiceService.createFromOrder(order, tenantId);

    log.info(
        "Sales order created: id={} tenant={} total={} invoice={}",
        order.getId(),
        tenantId,
        totalAmount,
        invoice.getInvoiceNumber());
    return Map.of(
        "order_id", order.getId(),
        "invoice_id", invoice.getId(),
        "invoice_number", invoice.getInvoiceNumber(),
        "total", totalAmount);
  }

  public Page<Order> getOrders(Pageable pageable) {
    Long tenantId = TenantContext.get();
    return orderRepository.findByTenantId(tenantId, pageable);
  }

  public Page<Order> getOrdersByType(String type, Pageable pageable) {
    Long tenantId = TenantContext.get();
    return orderRepository.findByTenantIdAndType(tenantId, type, pageable);
  }

  public Map<String, Object> getOrderWithItems(Long id) {
    Long tenantId = TenantContext.get();
    Order order =
        orderRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
    return Map.of("order", order, "items", items);
  }

  @Transactional
  public Order updateOrderStatus(Long id, String status) {
    Long tenantId = TenantContext.get();
    Order order =
        orderRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    order.setStatus(status);
    return orderRepository.save(order);
  }
}
