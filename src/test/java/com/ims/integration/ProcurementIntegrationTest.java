package com.ims.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ims.BaseIntegrationTest;
import com.ims.dto.request.CreateProductRequest;
import com.ims.helper.SecurityTestUtils;
import com.ims.model.Supplier;
import com.ims.order.dto.CreateOrderRequest;
import com.ims.order.dto.OrderItemRequest;
import com.ims.order.dto.OrderResponse;
import com.ims.order.entity.OrderType;
import com.ims.platform.repository.TenantRepository;
import com.ims.product.ProductService;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.PermissionRepository;
import com.ims.tenant.service.OrderService;
import com.ims.tenant.service.ReportService;
import com.ims.tenant.service.SupplierService;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcurementIntegrationTest extends BaseIntegrationTest {

  @Autowired private TenantRepository tenantRepository;

  @Autowired private SupplierService supplierService;

  @Autowired private ProductService productService;

  @Autowired private OrderService orderService;

  @Autowired private ReportService reportService;

  @Autowired private PermissionRepository permissionRepository;

  @Test
  @Transactional
  public void testFullProcurementWorkflow() {
    // 1. Setup Tenant
    com.ims.model.Tenant tenant =
        com.ims.model.Tenant.builder()
            .name("Test Pharmacy")
            .workspaceSlug("test-pharmacy-" + java.util.UUID.randomUUID())
            .companyCode(
                "TP-" + java.util.UUID.randomUUID().toString().substring(0, 4).toUpperCase())
            .businessType("PHARMACY")
            .maxProducts(100)
            .maxUsers(10)
            .build();
    tenant = tenantRepository.save(tenant);
    Long tenantId = tenant.getId();

    com.ims.model.User user =
        com.ims.model.User.builder()
            .name("Test User")
            .email("procure-" + java.util.UUID.randomUUID() + "@example.com")
            .passwordHash("secret")
            .role("ADMIN")
            .scope("TENANT")
            .tenantId(tenantId)
            .isActive(true)
            .isVerified(true)
            .build();
    user = userRepository.save(user);
    Long userId = user.getId();

    // Assign all permissions to pass @PreAuthorize checks
    var viewProductPerm =
        permissionRepository
            .findByKey("view_product")
            .orElseGet(
                () ->
                    permissionRepository.save(
                        com.ims.model.Permission.builder()
                            .key("view_product")
                            .description("View product details")
                            .build()));

    user.getCustomPermissions().add(viewProductPerm);
    user = userRepository.saveAndFlush(user);

    SecurityTestUtils.setAuthenticatedUser(user);
    TenantContext.setTenantId(tenantId);

    // 2. Create Supplier
    Supplier supplier =
        Supplier.builder()
            .name("Global Parts Inc")
            .email("info-" + java.util.UUID.randomUUID() + "@globalparts.com")
            .phone("1234567890")
            .tenantId(tenantId)
            .build();
    supplier = supplierService.create(supplier);
    assertNotNull(supplier.getId());

    // 3. Create Product
    CreateProductRequest productReq =
        CreateProductRequest.builder()
            .name("High Speed Gear")
            .sku("GEAR-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase())
            .purchasePrice(new BigDecimal("50.00"))
            .salePrice(new BigDecimal("75.00"))
            .build();
    var product = productService.createProduct(productReq);
    Long productId = product.getId();
    assertNotNull(productId);

    // 4. Create Purchase Order
    CreateOrderRequest orderReq =
        CreateOrderRequest.builder()
            .supplierId(supplier.getId())
            .type(OrderType.PURCHASE)
            .items(
                List.of(
                    OrderItemRequest.builder()
                        .productId(productId)
                        .quantity(10)
                        .unitPrice(new BigDecimal("50.00"))
                        .build()))
            .build();
    OrderResponse orderResult = orderService.createPurchaseOrder(tenantId, orderReq, userId);
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
    // 10 units * 50.0 purchase price = 500.0
    assertEquals(0, new BigDecimal("500.00").compareTo(valuation));

    // 8. Supplier Ledger
    Map<String, Object> ledger = supplierService.getSupplierLedger(supplier.getId());
    assertNotNull(ledger.get("orders"));
    assertEquals(1, ((java.util.List<?>) ledger.get("orders")).size());
  }
}
