package com.ims.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.order.dto.CreateOrderRequest;
import com.ims.order.dto.OrderItemRequest;
import com.ims.order.entity.OrderType;
import com.ims.product.ProductService;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.service.InvoiceService;
import com.ims.tenant.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.List;

class TransactionRollbackIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    private InvoiceService invoiceService;

    @BeforeEach
    void setup() {
        cleanupDatabase();
        TenantContext.setTenantId(testTenant1Id);
    }

    @Test
    void shouldRollbackStockDeductionWhenInvoiceFails() {
        // 1. Create product with stock
        CreateProductRequest createReq = CreateProductRequest.builder()
                .name("Rollback Test Product")
                .sku("RBK-001")
                .salePrice(BigDecimal.valueOf(100))
                .build();
        ProductResponse product = productService.createProduct(createReq);

        // Initial stock setup
        jdbcTemplate.update("UPDATE products SET stock = 10 WHERE id = ?", product.getId());
        jdbcTemplate.update(
                "INSERT INTO inventory (product_id, tenant_id, quantity, reserved_quantity, low_stock_threshold, reorder_level, version) VALUES (?, ?, 10, 0, 5, 5, 0)",
                product.getId(), testTenant1Id);

        // 2. Mock invoice service to fail
        doThrow(new RuntimeException("Invoice Generation Failed")).when(invoiceService).createFromOrder(any());

        // 3. Attempt to create sales order
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .type(OrderType.SALE)
                .items(List.of(OrderItemRequest.builder()
                        .productId(product.getId())
                        .quantity(3)
                        .build()))
                .build();

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            orderService.createSalesOrder(orderRequest, 1L);
        });

        // 4. Verify stock was NOT deducted (it was rolled back)
        Integer currentStock = jdbcTemplate.queryForObject("SELECT quantity FROM inventory WHERE product_id = ?",
                Integer.class, product.getId());
        assertThat(currentStock).isEqualTo(10);

        // 5. Verify no order was created
        Long orderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        assertThat(orderCount).isEqualTo(0);
    }
}
