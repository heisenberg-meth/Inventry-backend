package com.ims.integration;

import com.ims.BaseIntegrationTest;
import com.ims.model.Supplier;
import com.ims.shared.auth.TenantContext;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@org.springframework.security.test.context.support.WithMockUser(username = "admin", authorities = { "ADMIN",
                "ROLE_ADMIN", "create_product", "view_product", "update_product", "delete_product", "create_order",
                "view_order", "create_supplier", "view_supplier", "delete_supplier", "manage_stock", "view_stock" })
public class ProcurementIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private com.ims.platform.repository.TenantRepository tenantRepository;

        @Autowired
        private com.ims.tenant.service.SupplierService supplierService;

        @Autowired
        private com.ims.product.ProductService productService;

        @Autowired
        private com.ims.tenant.service.OrderService orderService;

        @Autowired
        private com.ims.tenant.service.ReportService reportService;

        @Test
        @Transactional
        public void testFullProcurementWorkflow() {
                // 1. Setup Tenant
                com.ims.model.Tenant tenant = com.ims.model.Tenant.builder()
                                .name("Test Pharmacy")
                                .workspaceSlug("test-pharmacy")
                                .companyCode("TP001")
                                .businessType("PHARMACY")
                                .maxProducts(100)
                                .maxUsers(10)
                                .build();
                tenant = tenantRepository.save(tenant);
                Long tenantId = tenant.getId();
                TenantContext.setTenantId(tenantId);

                com.ims.model.User user = com.ims.model.User.builder()
                                .name("Test User")
                                .email("test@example.com")
                                .passwordHash("secret")
                                .role("ADMIN")
                                .tenantId(tenantId)
                                .build();
                user = userRepository.save(user);
                Long userId = user.getId();

                // 2. Create Supplier
                Supplier supplier = Supplier.builder()
                                .name("Global Parts Inc")
                                .email("info@globalparts.com")
                                .phone("1234567890")
                                .tenantId(tenantId)
                                .build();
                supplier = supplierService.create(supplier);
                assertNotNull(supplier.getId());

                // 3. Create Product
                com.ims.dto.request.CreateProductRequest productReq = com.ims.dto.request.CreateProductRequest.builder()
                                .name("High Speed Gear")
                                .sku("GEAR-001")
                                .purchasePrice(new BigDecimal("50.00"))
                                .salePrice(new BigDecimal("100.00"))
                                .reorderLevel(5)
                                .build();
                var productResp = productService.createProduct(productReq);
                Long productId = productResp.getId();

                // 4. Create Purchase Order
                com.ims.order.dto.CreateOrderRequest orderReq = com.ims.order.dto.CreateOrderRequest.builder()
                                .supplierId(supplier.getId())
                                .type(com.ims.order.entity.OrderType.PURCHASE)
                                .items(java.util.List.of(
                                                com.ims.order.dto.OrderItemRequest.builder()
                                                                .productId(productId)
                                                                .quantity(10)
                                                                .unitPrice(new BigDecimal("50.00"))
                                                                .build()))
                                .build();
                com.ims.order.dto.OrderResponse orderResult = orderService.createPurchaseOrder(tenantId, orderReq,
                                userId);
                Long orderId = orderResult.getId();
                assertNotNull(orderId);

                // 5. Confirm and Complete Order (Stock In)
                orderService.confirmOrder(orderId, tenantId, userId);
                orderService.completeOrder(orderId, tenantId, userId);

                // 6. Verify Stock
                var updatedProduct = productService.getProductById(productId);
                assertEquals(10, updatedProduct.getStock());

                // 7. Verify Inventory Valuation (Report)
                BigDecimal valuation = reportService.getInventoryValuation();
                // 10 units * 50.00 purchase price = 500.00
                assertEquals(0, new BigDecimal("500.00").compareTo(valuation));

                // 8. Supplier Ledger
                Map<String, Object> ledger = supplierService.getSupplierLedger(supplier.getId());
                assertNotNull(ledger.get("orders"));
                assertEquals(1, ((java.util.List<?>) ledger.get("orders")).size());
        }
}
