package com.ims.order;

import com.ims.BaseIntegrationTest;
import com.ims.order.dto.CreateOrderRequest;
import com.ims.order.dto.OrderItemRequest;
import com.ims.order.dto.OrderResponse;
import com.ims.order.entity.OrderType;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.InsufficientStockException;
import com.ims.tenant.service.OrderService;
import com.ims.product.Product;
import com.ims.model.Inventory;
import com.ims.model.Customer;
import com.ims.model.Supplier;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrderManagementIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OrderService orderService;

    private Long productId;
    private Long customerId;
    private Long supplierId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(testTenant1Id);
        
        // Create Product
        Product product = Product.builder()
                .tenantId(testTenant1Id)
                .name("Test Product")
                .sku("TEST-SKU-1")
                .salePrice(new BigDecimal("100.00"))
                .purchasePrice(new BigDecimal("50.00"))
                .stock(10)
                .isDeleted(false)
                .build();
        product = productRepository.save(product);
        productId = product.getId();

        // Create Inventory
        Inventory inventory = Inventory.builder()
                .tenantId(testTenant1Id)
                .productId(productId)
                .quantity(10)
                .build();
        inventoryRepository.save(inventory);

        // Create Customer
        Customer customer = Customer.builder()
                .tenantId(testTenant1Id)
                .name("Test Customer")
                .build();
        customer = customerRepository.save(customer);
        customerId = customer.getId();

        // Create Supplier
        Supplier supplier = Supplier.builder()
                .tenantId(testTenant1Id)
                .name("Test Supplier")
                .build();
        supplier = supplierRepository.save(supplier);
        supplierId = supplier.getId();
    }

    @Test
    void testCreateSaleOrderSuccess() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .type(OrderType.SALE)
                .customerId(customerId)
                .items(List.of(OrderItemRequest.builder()
                        .productId(productId)
                        .quantity(2)
                        .build()))
                .build();

        OrderResponse response = orderService.createSalesOrder(request, 1L);

        assertNotNull(response.getId());
        assertEquals(OrderType.SALE, response.getType());
        assertEquals(8, inventoryRepository.findByProductIdAndTenantId(productId, testTenant1Id).get().getQuantity());
        assertEquals(8, productRepository.findById(productId).get().getStock());
        
        // Verify invoice
        assertTrue(invoiceRepository.existsByTenantIdAndOrderId(testTenant1Id, response.getId()));
    }

    @Test
    void testCreateSaleOrderInsufficientStock() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .type(OrderType.SALE)
                .customerId(customerId)
                .items(List.of(OrderItemRequest.builder()
                        .productId(productId)
                        .quantity(20)
                        .build()))
                .build();

        assertThrows(InsufficientStockException.class, () -> orderService.createSalesOrder(request, 1L));
        
        // Stock should remain unchanged
        assertEquals(10, inventoryRepository.findByProductIdAndTenantId(productId, testTenant1Id).get().getQuantity());
    }

    @Test
    void testCreatePurchaseOrderSuccess() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .type(OrderType.PURCHASE)
                .supplierId(supplierId)
                .items(List.of(OrderItemRequest.builder()
                        .productId(productId)
                        .quantity(5)
                        .build()))
                .build();

        OrderResponse response = orderService.createPurchaseOrder(request, 1L);

        assertNotNull(response.getId());
        assertEquals(OrderType.PURCHASE, response.getType());
        assertEquals(15, inventoryRepository.findByProductIdAndTenantId(productId, testTenant1Id).get().getQuantity());
        assertEquals(15, productRepository.findById(productId).get().getStock());
        
        // No invoice for purchase order
        assertFalse(invoiceRepository.existsByTenantIdAndOrderId(testTenant1Id, response.getId()));
    }

    @Test
    void testTenantIsolation() {
        // Tenant 2 tries to buy Tenant 1's product
        TenantContext.setTenantId(testTenant2Id);
        
        CreateOrderRequest request = CreateOrderRequest.builder()
                .type(OrderType.SALE)
                .items(List.of(OrderItemRequest.builder()
                        .productId(productId) // Belongs to Tenant 1
                        .quantity(1)
                        .build()))
                .build();

        // Should throw EntityNotFoundException because findByIdWithLock uses tenantId
        assertThrows(EntityNotFoundException.class, () -> orderService.createSalesOrder(request, 1L));
    }
}
