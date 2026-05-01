package com.ims.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.ims.BaseIntegrationTest;
import com.ims.category.Category;
import com.ims.shared.auth.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TenantIsolationIntegrationTest extends BaseIntegrationTest {

  @Override
  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    cleanupDatabase();
  }

  @Test
  void testRequestFailsWithoutTenantHeader() throws Exception {
    mockMvc.perform(get("/api/v1/tenant/categories")).andExpect(status().isUnauthorized());
  }

  @Test
  void testCreateCategoryWithTenantIsolation() throws Exception {
    TenantContext.setTenantId(testTenant1Id);
    Category category = new Category();
    category.setName("Test Category");
    category.setTenantId(testTenant1Id);
    TenantContext.clear();

    TenantContext.setTenantId(testTenant1Id);
    var t1Categories = categoryRepository.findAll();
    TenantContext.clear();

    TenantContext.setTenantId(testTenant2Id);
    var t2Categories = categoryRepository.findAll();
    TenantContext.clear();

    org.junit.jupiter.api.Assertions.assertEquals(1, t1Categories.size());
    org.junit.jupiter.api.Assertions.assertEquals(0, t2Categories.size());
    org.junit.jupiter.api.Assertions.assertEquals("Test Category", t1Categories.get(0).getName());
  }

  @Test
  void testDataIsolationBetweenTenants() throws Exception {
    TenantContext.setTenantId(testTenant1Id);
    Category cat1 = new Category();
    cat1.setName("Tenant 1 Category");
    cat1.setTenantId(testTenant1Id);
    categoryRepository.save(cat1);
    TenantContext.clear();

    TenantContext.setTenantId(testTenant1Id);
    var t1Categories = categoryRepository.findAll();
    TenantContext.clear();

    TenantContext.setTenantId(testTenant2Id);
    var t2Categories = categoryRepository.findAll();
    TenantContext.clear();

    org.junit.jupiter.api.Assertions.assertEquals(1, t1Categories.size());
    org.junit.jupiter.api.Assertions.assertEquals("Tenant 1 Category", t1Categories.get(0).getName());
    org.junit.jupiter.api.Assertions.assertEquals(0, t2Categories.size());
  }
}