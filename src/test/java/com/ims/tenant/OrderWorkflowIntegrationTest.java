package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.request.LoginRequest;
import com.ims.dto.request.SignupRequest;
import com.ims.dto.response.LoginResponse;
import com.ims.dto.response.ProductResponse;
import com.ims.model.Customer;
import com.ims.model.Supplier;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.SignupService;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.service.CustomerService;
import com.ims.tenant.service.SupplierService;
import com.ims.tenant.repository.UserRepository;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.SupplierRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.ProductRepository;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.shared.audit.AuditLogRepository;
import com.ims.tenant.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public class OrderWorkflowIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SignupService signupService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private SupplierService supplierService;
  @Autowired private CustomerService customerService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private SupplierRepository supplierRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderItemRepository orderItemRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private StockMovementRepository stockMovementRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private RoleRepository roleRepository;

  @MockitoBean private RedisTemplate<String, Object> redisTemplate;
  @MockitoBean private ValueOperations<String, Object> valueOperations;
  @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
  @MockitoBean private org.springframework.cache.CacheManager cacheManager;
  @MockitoBean private org.springframework.cache.interceptor.CacheResolver tenantAwareCacheResolver;

  @BeforeEach
  void setup() {
    // Correct deletion order to respect FK constraints
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
  void testSaleOrderWorkflow() throws Exception {
    // 1. Setup Tenant and Data
    SignupRequest signup = createSignupRequest("Workflow Corp", "wf-corp", "admin@wf.com");
    signupService.signup(signup);
    
    Long tenantId = tenantRepository.findByWorkspaceSlug("wf-corp").orElseThrow().getId();
    String token = login("admin@wf.com", "password123", "wf-corp");

    Customer customer;
    try {
      TenantContext.set(tenantId);
      customer = customerService.create(Customer.builder().name("Test Customer").build());
    } finally {
      TenantContext.clear();
    }
    
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("Workflow Product");
    createReq.setSku("WF-001");
    createReq.setSalePrice(new BigDecimal("100.00"));
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
                "quantity", 50,
                "notes", "Initial Stock"
            ))))
        .andExpect(status().isOk());

    // 2. Create Sales Order (Status: PENDING)
    Map<String, Object> orderReq = Map.of(
        "customer_id", customer.getId(),
        "items", List.of(Map.of(
            "product_id", product.getId(),
            "quantity", 10,
            "unit_price", 100.00
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

    // 3. Verify stock NOT deducted yet (still 50)
    verifyStock(token, product.getId(), 50);

    // 4. Confirm Order (Status -> CONFIRMED, stock 50 -> 40)
    mockMvc.perform(post("/api/tenant/orders/" + orderId + "/confirm")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));

    verifyStock(token, product.getId(), 40);

    // 5. Cancel Order (Status -> CANCELLED, stock 40 -> 50)
    mockMvc.perform(post("/api/tenant/orders/" + orderId + "/cancel")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    verifyStock(token, product.getId(), 50);
  }

  @Test
  void testPurchaseOrderWorkflow() throws Exception {
    // 1. Setup Tenant and Data
    SignupRequest signup = createSignupRequest("Procure Corp", "procure", "admin@procure.com");
    signupService.signup(signup);
    
    Long tenantId = tenantRepository.findByWorkspaceSlug("procure").orElseThrow().getId();
    String token = login("admin@procure.com", "password123", "procure");

    Supplier supplier;
    try {
      TenantContext.set(tenantId);
      supplier = supplierService.create(Supplier.builder().name("Test Supplier").build());
    } finally {
      TenantContext.clear();
    }
    
    CreateProductRequest createReq = new CreateProductRequest();
    createReq.setName("Procure Product");
    createReq.setSku("PR-001");
    createReq.setSalePrice(new BigDecimal("100.00"));
    MvcResult prodResult = mockMvc.perform(post("/api/tenant/products")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isCreated())
        .andReturn();
    ProductResponse product = objectMapper.readValue(prodResult.getResponse().getContentAsString(), ProductResponse.class);

    // 2. Create Purchase Order (status: PENDING)
    Map<String, Object> orderReq = Map.of(
        "supplier_id", supplier.getId(),
        "items", List.of(Map.of(
            "product_id", product.getId(),
            "quantity", 20,
            "unit_price", 80.00
        ))
    );

    MvcResult orderResult = mockMvc.perform(post("/api/tenant/orders/purchase")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(orderReq)))
        .andExpect(status().isCreated())
        .andReturn();
    
    Map<String, Object> orderResponse = objectMapper.readValue(orderResult.getResponse().getContentAsString(), Map.class);
    Long orderId = Long.valueOf(orderResponse.get("order_id").toString());

    // 3. Verify stock NOT added yet (still 0)
    verifyStock(token, product.getId(), 0);

    // 4. Complete Order (Status -> RECEIVED, stock 0 -> 20)
    // We need to confirm it first
    mockMvc.perform(post("/api/tenant/orders/" + orderId + "/confirm")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/tenant/orders/" + orderId + "/complete")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RECEIVED"));

    verifyStock(token, product.getId(), 20);
  }

  private void verifyStock(String token, Long productId, int expected) throws Exception {
    mockMvc.perform(get("/api/tenant/products/" + productId)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stock").value(expected));
  }

  private SignupRequest createSignupRequest(String name, String workspaceSlug, String email) {
    SignupRequest req = new SignupRequest();
    req.setBusinessName(name);
    req.setBusinessType("RETAIL");
    req.setWorkspaceSlug(workspaceSlug);
    req.setOwnerName("Owner " + name);
    req.setOwnerEmail(email);
    req.setPassword("password123");
    return req;
  }

  private String login(String email, String password, String companyCode) throws Exception {
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail(email);
    loginRequest.setPassword(password);
    loginRequest.setCompanyCode(companyCode);
    
    MvcResult result = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginRequest)))
        .andExpect(status().isOk())
        .andReturn();

    LoginResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);
    return response.getAccessToken();
  }
}
