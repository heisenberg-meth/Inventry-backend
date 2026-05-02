package com.ims.product;

import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.PagedResponse;
import com.ims.dto.response.ProductResponse;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.platform.service.SystemConfigService;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.audit.AuditResource;
import com.ims.shared.auth.SecurityContextAccessor;
import com.ims.shared.rbac.RequiresPermission;
import com.ims.shared.utils.CsvExportService;
import com.ims.tenant.domain.pharmacy.PharmacyProduct;
import com.ims.tenant.domain.pharmacy.PharmacyProductRepository;
import com.ims.tenant.domain.warehouse.WarehouseProduct;
import com.ims.tenant.service.WarehouseProductRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

  private final ProductRepository productRepository;
  private final PharmacyProductRepository pharmacyProductRepository;
  private final WarehouseProductRepository warehouseProductRepository;
  private final TenantRepository tenantRepository;
  private final SystemConfigService systemConfigService;
  private final AuditLogService auditLogService;
  private final SecurityContextAccessor securityContextAccessor;
  private final CsvExportService csvExportService;

  private static final int DEFAULT_REORDER_LEVEL = 10;
  private static final int MAX_PAGE_SIZE = 100;

  /**
   * Fallback expiry-alert threshold (days) when the tenant has not configured
   * one.
   */
  private static final int DEFAULT_EXPIRY_THRESHOLD_DAYS = 30;

  @Cacheable(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver")
  public PagedResponse<ProductResponse> getProducts(Pageable pageable) {

    if (pageable.getPageSize() > MAX_PAGE_SIZE) {
      pageable = PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    Page<ProductResponse> page = productRepository
        .findAllWithDetails(pageable)
        .map(this::toResponse);
    return new PagedResponse<>(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }

  public List<ProductResponse> getNextProducts(
      Long lastId, int limit) {
    securityContextAccessor.requireTenantId();
    Pageable pageable = PageRequest.of(0, Math.min(limit, MAX_PAGE_SIZE));
    List<ProductResponse> list = productRepository.findNextProducts(lastId != null ? lastId : 0L, pageable)
        .stream()
        .map(this::toResponse)
        .collect(Collectors.toList());

    return Objects.requireNonNull(list);
  }

  @Cacheable(value = "products", key = "'id:' + #id", cacheResolver = "tenantAwareCacheResolver")
  public ProductResponse getProductById(Long id) {
    Objects.requireNonNull(id, "product id required");
    securityContextAccessor.requireTenantId();
    ProductResponse response = productRepository
        .findByIdWithDetails(id)
        .map(view -> toResponse(Objects.requireNonNull(view)))
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    return Objects.requireNonNull(response);
  }

  public Optional<Product> findByIdWithLock(Long id) {
    securityContextAccessor.requireTenantId();
    return productRepository.findByIdWithLock(id);
  }

  @Transactional
  @RequiresPermission("create_product")
  @Caching(evict = {
      @CacheEvict(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver"),
      @CacheEvict(value = "reports", key = "'stock-report'", cacheResolver = "tenantAwareCacheResolver"),
      @CacheEvict(value = "dashboard", key = "'dashboard'", cacheResolver = "tenantAwareCacheResolver")
  })
  public ProductResponse createProduct(CreateProductRequest request) {
    Objects.requireNonNull(request, "request body required");
    Long tenantId = securityContextAccessor.requireTenantId();

    if (tenantId != null) {
      var tenant = tenantRepository
          .findById(tenantId)
          .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
      if (tenant.getMaxProducts() != null) {
        long currentCount = productRepository.countActiveByTenant();
        if (currentCount >= tenant.getMaxProducts()) {
          throw new IllegalArgumentException(
              "Product limit reached for your plan (" + tenant.getMaxProducts() + ")");
        }
      }
    }

    String businessType = securityContextAccessor.getBusinessType().orElse(null);

    // Validate pharmacy products must have pharmacy_details and extension must be
    // enabled
    if ("PHARMACY".equals(businessType)) {
      if (!systemConfigService.isPharmacyEnabled()) {
        throw new IllegalStateException("Pharmacy extension is currently disabled globally");
      }
      if (request.getPharmacyDetails() == null) {
        throw new IllegalArgumentException("Pharmacy products require pharmacy_details");
      }
    }

    Product product = Objects.requireNonNull(
        Product.builder()
            .name(Objects.requireNonNull(request.getName()))
            .sku(Objects.requireNonNull(request.getSku()))
            .barcode(request.getBarcode())
            .categoryId(request.getCategoryId())
            .unit(request.getUnit() != null ? request.getUnit() : "PCS")
            .purchasePrice(request.getPurchasePrice() != null ? request.getPurchasePrice() : BigDecimal.ZERO)
            .salePrice(Objects.requireNonNull(request.getSalePrice()))
            .reorderLevel(
                request.getReorderLevel() != null
                    ? Objects.requireNonNull(request.getReorderLevel()).intValue()
                    : DEFAULT_REORDER_LEVEL)
            .stock(0)
            .active(true)
            .build());

    product = productRepository.save(product);

    auditLogService.logAudit(
        AuditAction.CREATE,
        AuditResource.PRODUCT,
        Objects.requireNonNull(product.getId()),
        "Created product: "
            + Objects.requireNonNull(product.getName())
            + " (SKU: "
            + Objects.requireNonNull(product.getSku())
            + ")");

    PharmacyProduct pp = null;
    WarehouseProduct wp = null;

    if ("PHARMACY".equals(businessType) && request.getPharmacyDetails() != null) {
      var pd = Objects.requireNonNull(request.getPharmacyDetails());
      pp = Objects.requireNonNull(
          PharmacyProduct.builder()
              .product(product)
              .batchNumber(pd.getBatchNumber())
              .expiryDate(LocalDate.parse(pd.getExpiryDate()))
              .manufacturer(pd.getManufacturer())
              .hsnCode(pd.getHsnCode())
              .schedule(pd.getSchedule())
              .build());
      pp = Objects.requireNonNull(pharmacyProductRepository.save(pp));
    }

    if ("WAREHOUSE".equals(businessType) && request.getWarehouseDetails() != null) {
      var wd = Objects.requireNonNull(request.getWarehouseDetails());
      wp = Objects.requireNonNull(
          WarehouseProduct.builder()
              .product(product)
              .storageLocation(wd.getStorageLocation())
              .zone(wd.getZone())
              .rack(wd.getRack())
              .bin(wd.getBin())
              .build());
      wp = warehouseProductRepository.save(wp);
    }

    return toResponse(product, pp, wp);
  }

  @Transactional
  @RequiresPermission("update_product")
  @Caching(evict = {
      @CacheEvict(value = "products", key = "'id:' + #id", cacheResolver = "tenantAwareCacheResolver"),
      @CacheEvict(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver"),
      @CacheEvict(value = "reports", key = "'stock-report'", cacheResolver = "tenantAwareCacheResolver"),
      @CacheEvict(value = "dashboard", key = "'dashboard'", cacheResolver = "tenantAwareCacheResolver")
  })
  public ProductResponse updateProduct(
      Long id, CreateProductRequest request) {
    Objects.requireNonNull(id, "product id required");
    Objects.requireNonNull(request, "request body required");
    Product tmpProduct = productRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    Product product = Objects.requireNonNull(tmpProduct);

    if (request.getName() != null) {
      product.setName(request.getName());
    }
    if (request.getSku() != null) {
      product.setSku(Objects.requireNonNull(request.getSku()));
    }
    if (request.getBarcode() != null) {
      product.setBarcode(Objects.requireNonNull(request.getBarcode()));
    }
    if (request.getCategoryId() != null) {
      product.setCategoryId(Objects.requireNonNull(request.getCategoryId()));
    }
    if (request.getUnit() != null) {
      product.setUnit(Objects.requireNonNull(request.getUnit()));
    }
    if (request.getPurchasePrice() != null) {
      product.setPurchasePrice(Objects.requireNonNull(request.getPurchasePrice()));
    }
    if (request.getSalePrice() != null) {
      product.setSalePrice(Objects.requireNonNull(request.getSalePrice()));
    }
    if (request.getReorderLevel() != null) {
      product.setReorderLevel(Objects.requireNonNull(request.getReorderLevel()).intValue());
    }
    product.setUpdatedAt(Objects.requireNonNull(LocalDateTime.now()));

    Product tmpSaved = productRepository.save(product);
    Product safeProduct = Objects.requireNonNull(tmpSaved);
    product = safeProduct;

    auditLogService.logAudit(
        AuditAction.UPDATE,
        AuditResource.PRODUCT,
        Objects.requireNonNull(product.getId()),
        "Updated product: " + Objects.requireNonNull(product.getName()));

    String businessType = securityContextAccessor.getBusinessType().orElse(null);
    PharmacyProduct pp = null;
    WarehouseProduct wp = null;

    // Pharmacy extension update
    if ("PHARMACY".equals(businessType) && request.getPharmacyDetails() != null) {
      var pd = Objects.requireNonNull(request.getPharmacyDetails());
      PharmacyProduct tmpPp = pharmacyProductRepository
          .findById(Objects.requireNonNull(product.getId()))
          .orElse(PharmacyProduct.builder().product(product).build());
      PharmacyProduct fetchedPp = Objects.requireNonNull(tmpPp);

      pp = Objects.requireNonNull(fetchedPp);

      if (pd.getBatchNumber() != null) {
        pp.setBatchNumber(pd.getBatchNumber());
      }
      if (pd.getExpiryDate() != null) {
        pp.setExpiryDate(LocalDate.parse(pd.getExpiryDate()));
      }
      if (pd.getManufacturer() != null) {
        pp.setManufacturer(pd.getManufacturer());
      }
      if (pd.getHsnCode() != null) {
        pp.setHsnCode(pd.getHsnCode());
      }
      if (pd.getSchedule() != null) {
        pp.setSchedule(pd.getSchedule());
      }

      PharmacyProduct tmpSavedPp = pharmacyProductRepository.save(pp);
      PharmacyProduct safePp = Objects.requireNonNull(tmpSavedPp);
      pp = safePp;
    }

    // Warehouse extension update
    if ("WAREHOUSE".equals(businessType) && request.getWarehouseDetails() != null) {
      var wd = Objects.requireNonNull(request.getWarehouseDetails());
      WarehouseProduct tmpWp = warehouseProductRepository
          .findById(Objects.requireNonNull(product.getId()))
          .orElse(WarehouseProduct.builder().product(product).build());
      WarehouseProduct fetchedWp = Objects.requireNonNull(tmpWp);

      wp = Objects.requireNonNull(fetchedWp);

      if (wd.getStorageLocation() != null) {
        wp.setStorageLocation(wd.getStorageLocation());
      }
      if (wd.getZone() != null) {
        wp.setZone(wd.getZone());
      }
      if (wd.getRack() != null) {
        wp.setRack(wd.getRack());
      }
      if (wd.getBin() != null) {
        wp.setBin(wd.getBin());
      }

      WarehouseProduct tmpSavedWp = warehouseProductRepository.save(wp);
      WarehouseProduct safeWp = Objects.requireNonNull(tmpSavedWp);
      wp = safeWp;
    }

    return Objects.requireNonNull(toResponse(product, pp, wp));
  }

  @Transactional
  @RequiresPermission("delete_product")
  @Caching(evict = {
      @CacheEvict(value = "products", key = "'id:' + #id", cacheResolver = "tenantAwareCacheResolver"),
      @CacheEvict(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver"),
      @CacheEvict(value = "reports", key = "'stock-report'", cacheResolver = "tenantAwareCacheResolver"),
      @CacheEvict(value = "dashboard", key = "'dashboard'", cacheResolver = "tenantAwareCacheResolver")
  })
  public void deleteProduct(Long id) {
    Product tmpProduct = productRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    Product product = Objects.requireNonNull(tmpProduct);
    product.setActive(false);
    product.setUpdatedAt(Objects.requireNonNull(LocalDateTime.now()));
    productRepository.save(product);

    auditLogService.logAudit(
        AuditAction.DELETE,
        AuditResource.PRODUCT,
        id,
        "Soft deleted product: " + Objects.requireNonNull(product.getName()));
  }

  @Transactional
  @RequiresPermission("create_product")
  public ProductResponse duplicateProduct(Long id) {
    Objects.requireNonNull(id, "product id required");
    Product tmpOriginal = productRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    Product original = Objects.requireNonNull(tmpOriginal);

    Product clone = Objects.requireNonNull(
        Product.builder()
            .name(Objects.requireNonNull(original.getName()) + " (Copy)")
            .sku(Objects.requireNonNull(generateUniqueSku(original.getSku())))
            .barcode("COPY-" + UUID.randomUUID().toString().substring(0, 8)) // Barcode should be unique
            .categoryId(original.getCategoryId())
            .unit(original.getUnit())
            .purchasePrice(original.getPurchasePrice())
            .salePrice(original.getSalePrice())
            .stock(0) // Reset stock
            .reorderLevel(original.getReorderLevel())
            .active(true)
            .build());

    Product savedProduct = Objects.requireNonNull(productRepository.save(clone));

    // Clone extensions
    String businessType = securityContextAccessor.getBusinessType().orElse("");
    PharmacyProduct pp = null;
    WarehouseProduct wp = null;

    if ("PHARMACY".equals(businessType)) {
      var originalPp = pharmacyProductRepository.findById(id).orElse(null);
      if (originalPp != null) {
        pp = PharmacyProduct.builder()
            .product(savedProduct)
            .batchNumber(originalPp.getBatchNumber())
            .expiryDate(originalPp.getExpiryDate())
            .manufacturer(originalPp.getManufacturer())
            .hsnCode(originalPp.getHsnCode())
            .schedule(originalPp.getSchedule())
            .build();
        pp = pharmacyProductRepository.save(pp);
      }
    } else if ("WAREHOUSE".equals(businessType)) {
      var originalWp = warehouseProductRepository.findById(id).orElse(null);
      if (originalWp != null) {
        wp = WarehouseProduct.builder()
            .product(savedProduct)
            .storageLocation(originalWp.getStorageLocation())
            .zone(originalWp.getZone())
            .rack(originalWp.getRack())
            .bin(originalWp.getBin())
            .build();
        wp = warehouseProductRepository.save(wp);
      }
    }

    auditLogService.logAudit(
        AuditAction.DUPLICATE_PRODUCT,
        AuditResource.PRODUCT,
        savedProduct.getId(),
        "Duplicated from product #" + id);

    return (pp != null || wp != null)
        ? toResponse(savedProduct, pp, wp)
        : toResponse(savedProduct);
  }

  private String generateUniqueSku(String originalSku) {
    if (originalSku == null) {
      return null;
    }
    // Remove existing -COPY or -COPY-N suffix to get base
    String baseSku = originalSku.replaceAll("-COPY(-\\d+)?$", "");
    String newSku = baseSku + "-COPY";

    // Bounded retry to avoid infinite loop (PRD principle)
    for (int counter = 1; counter <= 10; counter++) {
      if (!productRepository.existsBySku(newSku)) {
        return newSku;
      }
      newSku = baseSku + "-COPY-" + counter;
    }
    return newSku + "-" + UUID.randomUUID().toString().substring(0, 5);
  }

  public List<ProductResponse> getLowStockProducts() {
    securityContextAccessor.requireTenantId();

    List<ProductResponse> list = Objects.requireNonNull(productRepository.findLowStockByTenant()).stream()
        .map(this::toResponse)
        .collect(Collectors.toList());

    return Objects.requireNonNull(list);
  }

  public List<ProductResponse> getExpiringProducts(Integer days) {
    Long tenantId = securityContextAccessor.requireTenantId();

    String businessType = securityContextAccessor.getBusinessType().orElse(null);

    if (!"PHARMACY".equals(businessType)) {
      throw new IllegalArgumentException(
          "Expiring products endpoint is only available for PHARMACY tenants");
    }

    int thresholdDays;
    if (days != null && days > 0) {
      thresholdDays = days;
    } else {

      thresholdDays = tenantRepository
          .findById(tenantId)
          .map(Tenant::getExpiryThresholdDays)
          .orElse(DEFAULT_EXPIRY_THRESHOLD_DAYS);
    }

    LocalDate threshold = LocalDate.now().plusDays(thresholdDays);
    List<ProductResponse> list = Objects.requireNonNull(pharmacyProductRepository.findExpiring(threshold)).stream()
        .map(this::toResponse)
        .collect(Collectors.toList());

    return Objects.requireNonNull(list);
  }

  /**
   * Generates a CSV export of all active products for the current tenant.
   * Hardened against cross-tenant leakage.
   */
  @Transactional(readOnly = true)
  @RequiresPermission("export_products")
  public String exportProductsAsCsv() {
    securityContextAccessor.requireTenantId();
    var products = productRepository.findExportData();

    var data = products.stream()
        .map(
            p -> {
              Map<String, Object> map = new LinkedHashMap<>();
              map.put("ID", p.getId());
              map.put("Name", p.getName());
              map.put("SKU", p.getSku());
              map.put("Stock", p.getStock());
              map.put("Price", p.getSalePrice());
              map.put("CategoryID", p.getCategoryId());
              return map;
            })
        .collect(Collectors.toList());

    return csvExportService.exportToCsv(
        List.of("ID", "Name", "SKU", "Stock", "Price", "CategoryID"), data);
  }

  public PagedResponse<ProductResponse> searchProducts(
      String query, Pageable pageable) {
    Long tenantId = securityContextAccessor.requireTenantId();

    Page<ProductResponse> page = productRepository
        .searchFast(tenantId, query, pageable)
        .map(view -> toResponse(Objects.requireNonNull(view)));
    return new PagedResponse<>(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }

  private ProductResponse toResponse(Product product) {
    return toResponse(Objects.requireNonNull(product), null, null);
  }

  private ProductResponse toResponse(
      Product product, PharmacyProduct pp, WarehouseProduct wp) {
    ProductResponse.ProductResponseBuilder builder = ProductResponse.builder()
        .id(product.getId())
        .name(product.getName())
        .sku(product.getSku())
        .barcode(product.getBarcode())
        .categoryId(product.getCategoryId())
        .unit(product.getUnit())
        .purchasePrice(product.getPurchasePrice())
        .salePrice(product.getSalePrice())
        .stock(product.getStock())
        .reorderLevel(product.getReorderLevel())
        .isActive(product.isActive())
        .createdAt(product.getCreatedAt());

    if (pp != null) {
      builder
          .batchNumber(pp.getBatchNumber())
          .expiryDate(pp.getExpiryDate())
          .manufacturer(pp.getManufacturer())
          .hsnCode(pp.getHsnCode())
          .schedule(pp.getSchedule());
    }

    if (wp != null) {
      builder
          .storageLocation(wp.getStorageLocation())
          .zone(wp.getZone())
          .rack(wp.getRack())
          .bin(wp.getBin());
    }

    return Objects.requireNonNull(builder.build());
  }

  private ProductResponse toResponse(ProductListView view) {
    Objects.requireNonNull(view, "view cannot be null");
    return Objects.requireNonNull(
        ProductResponse.builder()
            .id(view.getId())
            .name(view.getName())
            .sku(view.getSku())
            .barcode(view.getBarcode())
            .categoryId(view.getCategoryId())
            .unit(view.getUnit())
            .purchasePrice(view.getPurchasePrice())
            .salePrice(view.getSalePrice())
            .stock(view.getStock())
            .reorderLevel(view.getReorderLevel())
            .isActive(view.getIsActive())
            .createdAt(view.getCreatedAt())
            .batchNumber(view.getBatchNumber())
            .expiryDate(view.getExpiryDate())
            .manufacturer(view.getManufacturer())
            .hsnCode(view.getHsnCode())
            .schedule(view.getSchedule())
            .storageLocation(view.getStorageLocation())
            .zone(view.getZone())
            .rack(view.getRack())
            .bin(view.getBin())
            .build());
  }

  private ProductResponse toResponse(ProductExpiryView view) {
    Objects.requireNonNull(view, "view cannot be null");
    return Objects.requireNonNull(
        ProductResponse.builder()
            .id(view.getId())
            .name(view.getName())
            .sku(view.getSku())
            .batchNumber(view.getBatchNumber())
            .expiryDate(view.getExpiryDate())
            .manufacturer(view.getManufacturer())
            .build());
  }
}
