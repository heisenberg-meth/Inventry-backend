package com.ims.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ims.dto.request.CreateProductRequest;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.domain.pharmacy.PharmacyProductRepository;
import com.ims.tenant.repository.ProductRepository;
import com.ims.tenant.service.ProductService;
import com.ims.tenant.service.WarehouseProductRepository;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheResolver;

@SpringBootTest
@ActiveProfiles("test")
@EnableCaching
public class ProductCacheIntegrationTest {

  @Autowired private ProductService productService;

  @Autowired private CacheManager cacheManager;
  
  @Autowired private CacheResolver tenantAwareCacheResolver;

  @MockBean private ProductRepository productRepository;
  @MockBean private PharmacyProductRepository pharmacyProductRepository;
  @MockBean private WarehouseProductRepository warehouseProductRepository;

  @Configuration
  @ComponentScan(basePackages = "com.ims")
  static class TestConfig {
    @Bean
    @Primary
    public CacheManager testCacheManager() {
      ConcurrentMapCacheManager mgr = new ConcurrentMapCacheManager();
      // Allow dynamic cache creation
      return mgr;
    }
  }

  @BeforeEach
  void setup() {
    TenantContext.set(1L);
    when(productRepository.findByIsActiveTrue(any()))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    when(productRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
    when(pharmacyProductRepository.findById(any())).thenReturn(Optional.empty());
    when(warehouseProductRepository.findById(any())).thenReturn(Optional.empty());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    cacheManager.getCacheNames().forEach(name -> {
        var cache = cacheManager.getCache(name);
        if (cache != null) {
            cache.clear();
        }
    });
  }

  @Test
  void testTenantAwareCaching() {
    // 1. First call for Tenant 1
    TenantContext.set(1L);
    productService.getProducts(1L, PageRequest.of(0, 10));

    // Verify cache "products:1" exists and has entries
    assertThat(cacheManager.getCache("products:1")).isNotNull();
    assertThat(cacheManager.getCache("products:1").get("list:0:10")).isNotNull();

    // 2. Call for Tenant 2
    TenantContext.set(2L);
    productService.getProducts(2L, PageRequest.of(0, 10));

    // Verify cache "products:2" exists and has entries
    assertThat(cacheManager.getCache("products:2")).isNotNull();
    assertThat(cacheManager.getCache("products:2").get("list:0:10")).isNotNull();

    // 3. Verify they are separate
    assertThat(cacheManager.getCache("products:1").get("list:0:10"))
        .isNotSameAs(cacheManager.getCache("products:2").get("list:0:10"));
  }

  @Test
  void testCacheEvictionIsolation() {
    // 1. Populate both caches
    TenantContext.set(1L);
    productService.getProducts(1L, PageRequest.of(0, 10));

    TenantContext.set(2L);
    productService.getProducts(2L, PageRequest.of(0, 10));

    // 2. Evict Tenant 1
    TenantContext.set(1L);
    CreateProductRequest req = new CreateProductRequest();
    req.setName("New Prod");
    productService.createProduct(req);

    // 3. Verify Tenant 1 cache is empty
    assertThat(cacheManager.getCache("products:1").get("list:0:10")).isNull();

    // 4. Verify Tenant 2 cache is STILL THERE
    assertThat(cacheManager.getCache("products:2").get("list:0:10")).isNotNull();
  }
}
