package com.ims.product;

import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.PagedResponse;
import com.ims.dto.response.ProductResponse;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.platform.service.SystemConfigService;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditResource;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.rbac.RequiresPermission;
import com.ims.tenant.domain.pharmacy.PharmacyProduct;
import com.ims.tenant.domain.pharmacy.PharmacyProductRepository;
import com.ims.tenant.domain.warehouse.WarehouseProduct;
import com.ims.tenant.service.WarehouseProductRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
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
  private final com.ims.shared.audit.AuditLogService auditLogService;

  private static final int DEFAULT_REORDER_LEVEL = 10;
  private static final int MAX_PAGE_SIZE = 100;

  /** Fallback expiry-alert threshold (days) when the tenant has not configured one. */
  private static final int DEFAULT_EXPIRY_THRESHOLD_DAYS = 30;

  @Cacheable(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver")
  public @NonNull PagedResponse<ProductResponse> getProducts(@NonNull Pageable pageable) {
    Long tenantId = getRequiredTenantId();

    if (pageable.getPageSize() > MAX_PAGE_SIZE) {
      log.warn(
          "Requested page size {} exceeds limit, capping to {}",
          pageable.getPageSize(),
          MAX_PAGE_SIZE);
      pageable = PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    Page<ProductResponse> page =
        productRepository
            .findAllWithDetails(tenantId, pageable)
            .map(view -> toResponse(java.util.Objects.requireNonNull(view)));
    return new PagedResponse<>(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }

  public @NonNull List<ProductResponse> getNextProducts(
      @NonNull Long tenantId, @Nullable Long lastId, int limit) {
    getRequiredTenantId(); // Just to ensure context is present if required, though we use parameter
    // tenantId
    Pageable pageable = PageRequest.of(0, Math.min(limit, MAX_PAGE_SIZE));
    List<ProductResponse> list =
        Objects.requireNonNull(productRepository.findNextProducts(tenantId, lastId, pageable))
            .stream()
            .map(view -> toResponse(java.util.Objects.requireNonNull(view)))
            .collect(Collectors.toList());

    return Objects.requireNonNull(list);
  }

  @Cacheable(value = "products", key = "'id:' + #id", cacheResolver = "tenantAwareCacheResolver")
  public @NonNull ProductResponse getProductById(@NonNull Long id) {
    Objects.requireNonNull(id, "product id required");
    ProductResponse response =
        productRepository
            .findByIdWithDetails(id)
            .map(view -> toResponse(java.util.Objects.requireNonNull(view)))
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    return Objects.requireNonNull(response);
  }

  public @NonNull java.util.Optional<Product> findByIdWithLock(@NonNull Long id) {
    return Objects.requireNonNull(productRepository.findByIdWithLock(id));
  }

  @Transactional
  @Caching(
      evict = {
        @CacheEvict(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(
            value = "reports",
            key = "'stock-report'",
            cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(
            value = "dashboard",
            key = "'dashboard'",
            cacheResolver = "tenantAwareCacheResolver")
      })
  public @NonNull ProductResponse createProduct(@NonNull CreateProductRequest request) {
    Objects.requireNonNull(request, "request body required");
    Long tenantId = getRequiredTenantId();

    if (tenantId != null) {
      var tenant =
          tenantRepository
              .findById(tenantId)
              .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
      if (tenant.getMaxProducts() != null) {
        long currentCount = productRepository.countActiveByTenant(tenantId);
        if (currentCount >= tenant.getMaxProducts()) {
          throw new IllegalArgumentException(
              "Product limit reached for your plan (" + tenant.getMaxProducts() + ")");
        }
      }
    }

    String businessType = getBusinessType();

    // Validate pharmacy products must have pharmacy_details and extension must be enabled
    if ("PHARMACY".equals(businessType)) {
      if (!systemConfigService.isPharmacyEnabled()) {
        throw new IllegalStateException("Pharmacy extension is currently disabled globally");
      }
      if (request.getPharmacyDetails() == null) {
        throw new IllegalArgumentException("Pharmacy products require pharmacy_details");
      }
    }

    Product product =
        Product.builder()
            .name(request.getName())
            .sku(request.getSku())
            .barcode(request.getBarcode())
            .categoryId(request.getCategoryId())
            .unit(request.getUnit())
            .purchasePrice(request.getPurchasePrice())
            .salePrice(request.getSalePrice())
            .reorderLevel(
                request.getReorderLevel() != null
                    ? request.getReorderLevel()
                    : DEFAULT_REORDER_LEVEL)
            .stock(0)
            .isActive(true)
            .build();

    Product tmpProduct = productRepository.save(product);
    Product safeProduct = Objects.requireNonNull(tmpProduct);
    product = safeProduct;

    auditLogService.logAudit(
        AuditAction.CREATE,
        AuditResource.PRODUCT,
        java.util.Objects.requireNonNull(product.getId()),
        "Created product: "
            + java.util.Objects.requireNonNull(product.getName())
            + " (SKU: "
            + java.util.Objects.requireNonNull(product.getSku())
            + ")");

    PharmacyProduct pp = null;
    WarehouseProduct wp = null;

    if ("PHARMACY".equals(businessType) && request.getPharmacyDetails() != null) {
      var pd = request.getPharmacyDetails();
      pp =
          PharmacyProduct.builder()
              .product(product)
              .batchNumber(pd.getBatchNumber())
              .expiryDate(LocalDate.parse(pd.getExpiryDate()))
              .manufacturer(pd.getManufacturer())
              .hsnCode(pd.getHsnCode())
              .schedule(pd.getSchedule())
              .build();
      PharmacyProduct tmpPp = pharmacyProductRepository.save(pp);
      PharmacyProduct safePp = Objects.requireNonNull(tmpPp);
      pp = safePp;
    }

    if ("WAREHOUSE".equals(businessType) && request.getWarehouseDetails() != null) {
      var wd = request.getWarehouseDetails();
      wp =
          WarehouseProduct.builder()
              .product(product)
              .storageLocation(wd.getStorageLocation())
              .zone(wd.getZone())
              .rack(wd.getRack())
              .bin(wd.getBin())
              .build();
      WarehouseProduct tmpWp = warehouseProductRepository.save(wp);
      WarehouseProduct safeWp = Objects.requireNonNull(tmpWp);
      wp = safeWp;
    }

    log.info(
        "Product created: id={} name={}",
        java.util.Objects.requireNonNull(product.getId()),
        java.util.Objects.requireNonNull(product.getName()));
    return Objects.requireNonNull(toResponse(product, pp, wp));
  }

  @Transactional
  @Caching(
      evict = {
        @CacheEvict(
            value = "products",
            key = "'id:' + #id",
            cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(
            value = "reports",
            key = "'stock-report'",
            cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(
            value = "dashboard",
            key = "'dashboard'",
            cacheResolver = "tenantAwareCacheResolver")
      })
  public @NonNull ProductResponse updateProduct(
      @NonNull Long id, @NonNull CreateProductRequest request) {
    Objects.requireNonNull(id, "product id required");
    Objects.requireNonNull(request, "request body required");
    Product tmpProduct =
        productRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    Product product = Objects.requireNonNull(tmpProduct);

    if (request.getName() != null) {
      product.setName(request.getName());
    }
    if (request.getSku() != null) {
      product.setSku(request.getSku());
    }
    if (request.getBarcode() != null) {
      product.setBarcode(request.getBarcode());
    }
    if (request.getCategoryId() != null) {
      product.setCategoryId(request.getCategoryId());
    }
    if (request.getUnit() != null) {
      product.setUnit(request.getUnit());
    }
    if (request.getPurchasePrice() != null) {
      product.setPurchasePrice(request.getPurchasePrice());
    }
    if (request.getSalePrice() != null) {
      product.setSalePrice(request.getSalePrice());
    }
    if (request.getReorderLevel() != null) {
      product.setReorderLevel(request.getReorderLevel());
    }
    product.setUpdatedAt(LocalDateTime.now());

    Product tmpSaved = productRepository.save(product);
    Product safeProduct = Objects.requireNonNull(tmpSaved);
    product = safeProduct;

    auditLogService.logAudit(
        AuditAction.UPDATE,
        AuditResource.PRODUCT,
        java.util.Objects.requireNonNull(product.getId()),
        "Updated product: " + java.util.Objects.requireNonNull(product.getName()));

    String businessType = getBusinessType();
    PharmacyProduct pp = null;
    WarehouseProduct wp = null;

    // Pharmacy extension update
    if ("PHARMACY".equals(businessType) && request.getPharmacyDetails() != null) {
      var pd = request.getPharmacyDetails();
      PharmacyProduct tmpPp =
          pharmacyProductRepository
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
      var wd = request.getWarehouseDetails();
      WarehouseProduct tmpWp =
          warehouseProductRepository
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
  @Caching(
      evict = {
        @CacheEvict(
            value = "products",
            key = "'id:' + #id",
            cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(
            value = "reports",
            key = "'stock-report'",
            cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(
            value = "dashboard",
            key = "'dashboard'",
            cacheResolver = "tenantAwareCacheResolver")
      })
  public void deleteProduct(@NonNull Long id) {
    Product tmpProduct =
        productRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    Product product = Objects.requireNonNull(tmpProduct);
    product.setIsActive(false);
    product.setUpdatedAt(LocalDateTime.now());
    productRepository.save(product);

    auditLogService.logAudit(
        AuditAction.DELETE,
        AuditResource.PRODUCT,
        id,
        "Soft deleted product: " + java.util.Objects.requireNonNull(product.getName()));

    log.info("Product soft deleted: id={}", id);
  }

  @Transactional
  public @NonNull ProductResponse duplicateProduct(@NonNull Long id) {
    Objects.requireNonNull(id, "product id required");
    Product tmpOriginal =
        productRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    Product original = Objects.requireNonNull(tmpOriginal);

    Long tenantId = Objects.requireNonNull(original.getTenantId());
    Product clone =
        Product.builder()
            .name(java.util.Objects.requireNonNull(original.getName()) + " (Copy)")
            .sku(generateUniqueSku(original.getSku(), tenantId))
            .barcode(null) // Barcode should be unique
            .categoryId(original.getCategoryId())
            .unit(original.getUnit())
            .purchasePrice(original.getPurchasePrice())
            .salePrice(original.getSalePrice())
            .stock(0) // Reset stock
            .reorderLevel(original.getReorderLevel())
            .isActive(true)
            .build();

    Product tmpSaved = productRepository.save(clone);
    Product savedProduct = Objects.requireNonNull(tmpSaved);
    log.info("Product duplicated: original_id={} new_id={}", id, savedProduct.getId());

    auditLogService.logAudit(
        AuditAction.DUPLICATE_PRODUCT,
        AuditResource.PRODUCT,
        savedProduct.getId(),
        "Duplicated from product #" + id);

    ProductResponse fallback = toResponse(Objects.requireNonNull(savedProduct));

    ProductResponse result =
        productRepository
            .findByIdWithDetails(savedProduct.getId())
            .map(this::toResponse)
            .orElse(fallback);

    return Objects.requireNonNull(result);
  }

  private @Nullable String generateUniqueSku(@Nullable String originalSku, @NonNull Long tenantId) {
    if (originalSku == null) {
      return null;
    }
    // Remove existing -COPY or -COPY-N suffix to get base
    String baseSku = originalSku.replaceAll("-COPY(-\\d+)?$", "");
    String newSku = baseSku + "-COPY";
    int counter = 1;
    while (productRepository.existsBySkuAndTenantId(newSku, tenantId)) {
      newSku = baseSku + "-COPY-" + counter++;
    }
    return newSku;
  }

  public @NonNull List<ProductResponse> getLowStockProducts() {
    Long tenantId = getRequiredTenantId();

    List<ProductResponse> list =
        Objects.requireNonNull(productRepository.findLowStockByTenant(tenantId)).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());

    return Objects.requireNonNull(list);
  }

  public @NonNull List<ProductResponse> getExpiringProducts(@Nullable Integer days) {
    Long tenantId = getRequiredTenantId();

    String businessType = getBusinessType();

    if (!"PHARMACY".equals(businessType)) {
      throw new IllegalArgumentException(
          "Expiring products endpoint is only available for PHARMACY tenants");
    }

    int thresholdDays;
    if (days != null && days > 0) {
      thresholdDays = days;
    } else {

      thresholdDays =
          tenantRepository
              .findById(tenantId)
              .map(Tenant::getExpiryThresholdDays)
              .orElse(DEFAULT_EXPIRY_THRESHOLD_DAYS);
    }

    LocalDate threshold = LocalDate.now().plusDays(thresholdDays);
    List<ProductResponse> list =
        Objects.requireNonNull(pharmacyProductRepository.findExpiring(threshold)).stream()
            .map(pp -> toResponseWithPharmacy(Objects.requireNonNull(pp.getProduct()), pp))
            .collect(Collectors.toList());

    return Objects.requireNonNull(list);
  }

  public @NonNull PagedResponse<ProductResponse> searchProducts(
      @NonNull String query, @NonNull Pageable pageable) {
    Long tenantId = getRequiredTenantId();

    Page<ProductResponse> page =
        productRepository
            .searchFast(tenantId, query, pageable)
            .map(view -> toResponse(java.util.Objects.requireNonNull(view)));
    return new PagedResponse<>(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }

  private @NonNull Long getRequiredTenantId() {
    Long tenantId = getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException(
          "Tenant not found in security context");
    }
    return Objects.requireNonNull(tenantId);
  }

  private @Nullable String getBusinessType() {
    try {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
        return details.getBusinessType();
      }
    } catch (Exception e) {
      log.trace("Caught expected exception in business type retrieval: {}", e.getMessage());
    }
    return null;
  }

  private @Nullable Long getTenantId() {
    try {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
        return details.getTenantId();
      }
    } catch (Exception e) {
      log.trace("Caught expected exception in tenant id retrieval: {}", e.getMessage());
    }
    return null;
  }

  private ProductResponse toResponse(@NonNull Product product) {
    return toResponse(Objects.requireNonNull(product), null, null);
  }

  private ProductResponse toResponse(
      @NonNull Product product, @Nullable PharmacyProduct pp, @Nullable WarehouseProduct wp) {
    ProductResponse.ProductResponseBuilder builder =
        ProductResponse.builder()
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
            .isActive(product.getIsActive())
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

  private @NonNull ProductResponse toResponse(@NonNull ProductListView view) {
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

  private ProductResponse toResponseWithPharmacy(
      @NonNull Product product, @NonNull PharmacyProduct pp) {
    return Objects.requireNonNull(
        ProductResponse.builder()
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
            .isActive(product.getIsActive())
            .createdAt(product.getCreatedAt())
            .batchNumber(pp.getBatchNumber())
            .expiryDate(pp.getExpiryDate())
            .manufacturer(pp.getManufacturer())
            .hsnCode(pp.getHsnCode())
            .schedule(pp.getSchedule())
            .build());
  }
}
