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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.CacheManager;
import java.util.Objects;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootTest
@ActiveProfiles("test")
@EnableCaching
public class ProductCacheIntegrationTest {

  @Autowired private ProductService productService;

  @Autowired private CacheManager cacheManager;
  

  @MockitoBean private ProductRepository productRepository;
  @MockitoBean private PharmacyProductRepository pharmacyProductRepository;
  @MockitoBean private WarehouseProductRepository warehouseProductRepository;

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
  @SuppressWarnings("null")
  void setup() {
    TenantContext.set(1L);
    when(productRepository.findByIsActiveTrue(any()))
        .thenReturn(new PageImpl<>(Objects.requireNonNull(Collections.emptyList())));
    
    when(productRepository.save(any())).thenAnswer(i -> {
        com.ims.model.Product p = i.getArgument(0);
        return Objects.requireNonNull(p);
    });

    when(pharmacyProductRepository.findById(Objects.requireNonNull(any()))).thenReturn(Optional.empty());
    when(warehouseProductRepository.findById(Objects.requireNonNull(any()))).thenReturn(Optional.empty());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    cacheManager.getCacheNames().forEach(name -> {
        var cache = cacheManager.getCache(Objects.requireNonNull(name));
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
    assertThat(Objects.requireNonNull(cacheManager.getCache("products:1")).get("list:0:10")).isNotNull();

    // 2. Call for Tenant 2
    TenantContext.set(2L);
    productService.getProducts(2L, PageRequest.of(0, 10));

    // Verify cache "products:2" exists and has entries
    assertThat(cacheManager.getCache("products:2")).isNotNull();
    assertThat(Objects.requireNonNull(cacheManager.getCache("products:2")).get("list:0:10")).isNotNull();

    // 3. Verify they are separate
    assertThat(Objects.requireNonNull(cacheManager.getCache("products:1")).get("list:0:10"))
        .isNotSameAs(Objects.requireNonNull(cacheManager.getCache("products:2")).get("list:0:10"));
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
    assertThat(Objects.requireNonNull(cacheManager.getCache("products:1")).get("list:0:10")).isNull();

    // 4. Verify Tenant 2 cache is STILL THERE
    assertThat(Objects.requireNonNull(cacheManager.getCache("products:2")).get("list:0:10")).isNotNull();
  }
}
