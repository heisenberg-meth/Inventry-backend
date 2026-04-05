package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.dto.response.ProductResponse;
import com.ims.model.Customer;
import com.ims.model.Invoice;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.SignupService;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.service.CustomerService;
import com.ims.tenant.repository.UserRepository;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.ProductRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.shared.audit.AuditLogRepository;
import com.ims.tenant.repository.RoleRepository;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BillingIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SignupService signupService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private CustomerService customerService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderItemRepository orderItemRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private StockMovementRepository stockMovementRepository;
  @Autowired private SupplierRepository supplierRepository;

  @MockitoBean private RedisTemplate<String, Object> redisTemplate;
  @MockitoBean private ValueOperations<String, Object> valueOperations;
  @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
  @MockitoBean private org.springframework.cache.CacheManager cacheManager;
  @MockitoBean private org.springframework.cache.interceptor.CacheResolver tenantAwareCacheResolver;

  @BeforeEach
  void setup() {
    auditLogRepository.deleteAllInBatch();
    invoiceRepository.deleteAllInBatch();
    orderItemRepository.deleteAllInBatch();
    orderRepository.deleteAllInBatch();
    stockMovementRepository.deleteAllInBatch();
    productRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
    roleRepository.deleteAllInBatch();
    customerRepository.deleteAllInBatch();
    supplierRepository.deleteAllInBatch();
    tenantRepository.deleteAllInBatch();
    
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(anyString())).thenReturn(1L);
    
    org.springframework.cache.Cache dummyCache = new org.springframework.cache.concurrent.ConcurrentMapCache("dummy");
    doReturn(java.util.Collections.singletonList(dummyCache)).when(tenantAwareCacheResolver).resolveCaches(any());
    when(cacheManager.getCache(anyString())).thenReturn(dummyCache);
  }

  @Test
  void testInvoiceGenerationAndPdfDownload() throws Exception {
    // 1. Setup Tenant and Data
    SignupRequest signup = new SignupRequest();
    signup.setBusinessName("Billing Corp");
    signup.setWorkspaceSlug("billing-corp");
    signup.setBusinessType("RETAIL");
    signup.setOwnerName("Admin");
    signup.setOwnerEmail("admin@billing.com");
    signup.setPassword("password123");
    signup.setAddress("456 Business Park, Industrial Area");
    signup.setGstin("29ABCDE1234F1Z5");
    signupService.signup(signup);
    
    Long tenantId = tenantRepository.findByWorkspaceSlug("billing-corp").orElseThrow().getId();
    String token = login("admin@billing.com", "password123", "billing-corp");

    Customer customer;
    try {
      TenantContext.set(tenantId);
      customer = customerService.create(Customer.builder().name("Billing Customer").address("123 Street").build());
    } finally {
      TenantContext.clear();
    }
    
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("Billing Product");
    createReq.setSku("BILL-001");
    createReq.setSalePrice(new BigDecimal("150.00"));
    MvcResult prodResult = mockMvc.perform(post("/api/tenant/products")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isCreated())
        .andReturn();
    ProductResponse product = objectMapper.readValue(prodResult.getResponse().getContentAsString(), ProductResponse.class);

    // Add initial stock
    mockMvc.perform(post("/api/tenant/stock/in")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "product_id", product.getId(),
                "quantity", 100,
                "notes", "Initial Stock"
            ))))
        .andExpect(status().isOk());

    // 2. Create Sales Order
    Map<String, Object> orderReq = Map.of(
        "customer_id", customer.getId(),
        "items", List.of(Map.of(
            "product_id", product.getId(),
            "quantity", 2,
            "unit_price", 150.00
        ))
    );

    MvcResult orderResult = mockMvc.perform(post("/api/tenant/orders/sale")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(orderReq)))
        .andExpect(status().isCreated())
        .andReturn();
    
    Map<String, Object> orderResponse = objectMapper.readValue(orderResult.getResponse().getContentAsString(), Map.class);
    Long orderId = Long.valueOf(orderResponse.get("order_id").toString());

    // 3. Confirm Order (Triggers Invoice Generation)
    mockMvc.perform(post("/api/tenant/orders/" + orderId + "/confirm")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // 4. Verify Invoice Exists
    MvcResult invoicesResult = mockMvc.perform(get("/api/tenant/invoices")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].orderId").value(orderId))
        .andReturn();
    
    String invoicesJson = invoicesResult.getResponse().getContentAsString();
    Long invoiceId = objectMapper.readTree(invoicesJson).get("content").get(0).get("id").asLong();

    // 5. Download PDF
    mockMvc.perform(get("/api/tenant/invoices/" + invoiceId + "/pdf")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(header().string("Content-Disposition", "attachment; filename=invoice-" + invoiceId + ".pdf"));
  }

  private String login(String email, String password, String workspace) throws Exception {
    Map<String, String> loginReq = Map.of(
        "email", email,
        "password", password,
        "companyCode", workspace
    );
    MvcResult result = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginReq)))
        .andExpect(status().isOk())
        .andReturn();
    String content = result.getResponse().getContentAsString();
    return objectMapper.readTree(content).get("accessToken").asText();
  }
}
