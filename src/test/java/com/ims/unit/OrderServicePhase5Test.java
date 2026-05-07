package com.ims.unit;

import com.ims.model.Order;
import com.ims.model.OrderItem;
import com.ims.order.dto.CreateOrderRequest;
import com.ims.order.dto.OrderItemRequest;
import com.ims.order.dto.OrderResponse;
import com.ims.order.entity.OrderStatus;
import com.ims.order.entity.OrderType;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.InsufficientStockException;
import com.ims.shared.metrics.BusinessMetricsService;
import com.ims.shared.outbox.OutboxService;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.pdf.PdfService;
import com.ims.tenant.repository.SupplierRepository;
import com.ims.tenant.service.InvoiceService;
import com.ims.tenant.service.OrderService;
import com.ims.tenant.service.InventoryService;
import com.ims.product.Product;
import com.ims.product.ProductRepository;
import com.ims.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServicePhase5Test {

        @Mock
        private OrderRepository orderRepository;
        @Mock
        private OrderItemRepository orderItemRepository;
        @Mock
        private ProductRepository productRepository;
        @Mock
        private ProductService productService;
        @Mock
        private InventoryService inventoryService;
        @Mock
        private InvoiceService invoiceService;
        @Mock
        private CustomerRepository customerRepository;
        @Mock
        private SupplierRepository supplierRepository;
        @Mock
        private TenantRepository tenantRepository;
        @Mock
        private PdfService pdfService;
        @Mock
        private AuditLogService auditLogService;
        @Mock
        private OutboxService outboxService;
        @Mock
        private BusinessMetricsService businessMetricsService;

        @InjectMocks
        private OrderService orderService;

        @BeforeEach
        void setUp() {
                TenantContext.setTenantId(1L);
        }

        @Test
        void createSalesOrder_ShouldBePendingInitially() {
                // Arrange
                Long userId = 100L;
                Long productId = 1L;
                CreateOrderRequest request = CreateOrderRequest.builder()
                                .type(OrderType.SALE)
                                .customerId(10L)
                                .items(List.of(OrderItemRequest.builder()
                                                .productId(productId)
                                                .quantity(2)
                                                .build()))
                                .build();

                Product product = Product.builder()
                                .id(productId)
                                .name("Test Product")
                                .stock(10)
                                .salePrice(new BigDecimal("100.00"))
                                .build();

                when(customerRepository.findByIdAndTenantId(anyLong(), anyLong()))
                                .thenReturn(Optional.of(mock(com.ims.model.Customer.class)));
                when(productService.findByIdWithLock(productId)).thenReturn(Optional.of(product));
                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                        Order o = invocation.getArgument(0);
                        o.setId(1L);
                        return o;
                });
                when(inventoryService.getAvailableStock(anyLong(), eq(productId))).thenReturn(10);

                // Act
                OrderResponse result = orderService.createSalesOrder(1L, request, userId);

                // Assert
                assertNotNull(result.getId());
                assertEquals(OrderStatus.PENDING, result.getStatus());

                // Stock should NOT be deducted yet
                verify(inventoryService, never()).decreaseStock(anyLong(), anyLong(), anyInt(), anyString(), anyLong());

                // 3. Create order happened as PENDING
                verify(orderRepository).save(
                                argThat(o -> o.getType() == OrderType.SALE && o.getStatus() == OrderStatus.PENDING));

                // 4. Create order items happened
                verify(orderItemRepository).save(any(OrderItem.class));

                // 5. Invoice should NOT be generated yet
                verify(invoiceService, never()).createFromOrder(any(Order.class));
        }

        @Test
        void createSalesOrder_InsufficientStock_ShouldThrowException() {
                // Arrange
                Long userId = 100L;
                Long productId = 1L;
                CreateOrderRequest request = CreateOrderRequest.builder()
                                .type(OrderType.SALE)
                                .items(List.of(OrderItemRequest.builder()
                                                .productId(productId)
                                                .quantity(20)
                                                .build()))
                                .build();

                Product product = Product.builder()
                                .id(productId)
                                .name("Test Product")
                                .stock(10)
                                .build();

                lenient().when(productService.findByIdWithLock(productId)).thenReturn(Optional.of(product));
                lenient().when(inventoryService.getAvailableStock(anyLong(), eq(productId))).thenReturn(10);

                // Act & Assert
                assertThrows(InsufficientStockException.class,
                                () -> orderService.createSalesOrder(1L, request, userId));
                verify(inventoryService, never()).decreaseStock(anyLong(), anyLong(), anyInt(), anyString(), anyLong());
                verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void createPurchaseOrder_ShouldBePendingInitially() {
                // Arrange
                Long userId = 100L;
                Long productId = 1L;
                Long supplierId = 5L;
                CreateOrderRequest request = CreateOrderRequest.builder()
                                .type(OrderType.PURCHASE)
                                .supplierId(supplierId)
                                .items(List.of(OrderItemRequest.builder()
                                                .productId(productId)
                                                .quantity(5)
                                                .build()))
                                .build();

                Product product = Product.builder()
                                .id(productId)
                                .purchasePrice(new BigDecimal("50.00"))
                                .build();

                when(supplierRepository.findActiveByIdAndTenantId(eq(supplierId), anyLong()))
                                .thenReturn(Optional.of(mock(com.ims.model.Supplier.class)));
                when(productService.findByIdWithLock(productId)).thenReturn(Optional.of(product));
                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                        Order o = invocation.getArgument(0);
                        o.setId(2L);
                        return o;
                });

                // Act
                OrderResponse result = orderService.createPurchaseOrder(1L, request, userId);

                // Assert
                assertNotNull(result.getId());
                assertEquals(OrderStatus.PENDING, result.getStatus());
                verify(inventoryService, never()).increaseStock(anyLong(), anyLong(), anyInt(), anyString(), anyLong());
                verify(orderRepository)
                                .save(argThat(o -> o.getType() == OrderType.PURCHASE
                                                && o.getStatus() == OrderStatus.PENDING));
        }
}
