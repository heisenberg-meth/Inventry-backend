package com.ims.product;

import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditResource;

import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.model.Tenant;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.rbac.RequiresPermission;
import com.ims.tenant.domain.pharmacy.PharmacyProduct;
import com.ims.tenant.domain.pharmacy.PharmacyProductRepository;
import com.ims.tenant.domain.warehouse.WarehouseProduct;
import com.ims.tenant.service.WarehouseProductRepository;
import com.ims.platform.repository.TenantRepository;
import com.ims.platform.service.SystemConfigService;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import java.util.Objects;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  @Cacheable(
      cacheResolver = "tenantAwareCacheResolver",
      value = "products",
      key = "#root.target.getSafeTenantKey('list:' + (#pageable?.pageNumber ?: 0) + ':' + (#pageable?.pageSize ?: 10) + ':' + (#pageable?.sort?.toString() ?: ''))")
  public Page<ProductResponse> getProducts(Pageable pageable) {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            log.error("Tenant ID is missing in ProductService.getProducts");
            throw new IllegalArgumentException("Tenant context is missing");
        }
        log.info("TenantContext: {}", tenantId);


    if (pageable.getPageSize() > MAX_PAGE_SIZE) {
        log.warn("Requested page size {} exceeds limit, capping to {}", pageable.getPageSize(), MAX_PAGE_SIZE);
        pageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    return productRepository.findAllWithDetails(tenantId, pageable).map(this::toResponse);
  }

  public List<ProductResponse> getNextProducts(Long tenantId, Long lastId, int limit) {
      if (tenantId == null) throw new IllegalArgumentException("Tenant context missing");
      Pageable pageable = org.springframework.data.domain.PageRequest.of(0, Math.min(limit, MAX_PAGE_SIZE));
      return productRepository.findNextProducts(tenantId, lastId, pageable).stream()
          .map(this::toResponse)
          .collect(Collectors.toList());
  }

  @SuppressWarnings("null")
  public ProductResponse getProductById(@NonNull Long id) {
    Product product =
        productRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    return toResponse(product);
  }

  public java.util.Optional<Product> findByIdWithLock(Long id) {
    return productRepository.findByIdWithLock(id);
  }

  @SuppressWarnings("null")
  @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "products", allEntries = true)
  public ProductResponse createProduct(CreateProductRequest request) {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            log.error("Tenant ID is missing in ProductService.createProduct");
            throw new IllegalStateException("Tenant not resolved from request");
        }
        log.info("TenantContext: {}", tenantId);

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
                request.getReorderLevel() != null ? request.getReorderLevel() : DEFAULT_REORDER_LEVEL)
            .stock(0)
            .isActive(true)
            .build();

    product = productRepository.save(product);

    auditLogService.logAudit(
        AuditAction.CREATE,
        AuditResource.PRODUCT,
        product.getId(),
        "Created product: " + product.getName() + " (SKU: " + product.getSku() + ")");

    // Pharmacy extension — same @Transactional
    if ("PHARMACY".equals(businessType) && request.getPharmacyDetails() != null) {
      var pd = request.getPharmacyDetails();
      PharmacyProduct pp =
          PharmacyProduct.builder()
              .product(product)
              .batchNumber(pd.getBatchNumber())
              .expiryDate(LocalDate.parse(pd.getExpiryDate()))
              .manufacturer(pd.getManufacturer())
              .hsnCode(pd.getHsnCode())
              .schedule(pd.getSchedule())
              .build();
      pharmacyProductRepository.save(pp);
    }

    // Warehouse extension — same @Transactional
    if ("WAREHOUSE".equals(businessType) && request.getWarehouseDetails() != null) {
      var wd = request.getWarehouseDetails();
      WarehouseProduct wp =
          WarehouseProduct.builder()
              .product(product)
              .storageLocation(wd.getStorageLocation())
              .zone(wd.getZone())
              .rack(wd.getRack())
              .bin(wd.getBin())
              .build();
      warehouseProductRepository.save(wp);
    }

    log.info("Product created: id={} name={}", product.getId(), product.getName());
    return toResponse(product);
  }

  @SuppressWarnings("null")
  @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "products", allEntries = true)
  public ProductResponse updateProduct(@NonNull Long id, CreateProductRequest request) {
    Product product =
        productRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

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

    product = productRepository.save(product);

    auditLogService.logAudit(
        AuditAction.UPDATE,
        AuditResource.PRODUCT,
        product.getId(),
        "Updated product: " + product.getName());

    String businessType = getBusinessType();

    // Pharmacy extension update
    if ("PHARMACY".equals(businessType) && request.getPharmacyDetails() != null) {
      var pd = request.getPharmacyDetails();
      PharmacyProduct pp =
          pharmacyProductRepository
              .findById(Objects.requireNonNull(product.getId()))
              .orElse(PharmacyProduct.builder().product(product).build());

      if (pd.getBatchNumber() != null) pp.setBatchNumber(pd.getBatchNumber());
      if (pd.getExpiryDate() != null) pp.setExpiryDate(LocalDate.parse(pd.getExpiryDate()));
      if (pd.getManufacturer() != null) pp.setManufacturer(pd.getManufacturer());
      if (pd.getHsnCode() != null) pp.setHsnCode(pd.getHsnCode());
      if (pd.getSchedule() != null) pp.setSchedule(pd.getSchedule());

      pharmacyProductRepository.save(pp);
    }

    // Warehouse extension update
    if ("WAREHOUSE".equals(businessType) && request.getWarehouseDetails() != null) {
      var wd = request.getWarehouseDetails();
      WarehouseProduct wp =
          warehouseProductRepository
              .findById(Objects.requireNonNull(product.getId()))
              .orElse(WarehouseProduct.builder().product(product).build());

      if (wd.getStorageLocation() != null) wp.setStorageLocation(wd.getStorageLocation());
      if (wd.getZone() != null) wp.setZone(wd.getZone());
      if (wd.getRack() != null) wp.setRack(wd.getRack());
      if (wd.getBin() != null) wp.setBin(wd.getBin());

      warehouseProductRepository.save(wp);
    }

    return toResponse(product);
    }

    @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "products", allEntries = true)
  @RequiresPermission("delete_product")
  public void deleteProduct(@NonNull Long id) {
    Product product =
        productRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    product.setIsActive(false);
    product.setUpdatedAt(LocalDateTime.now());
    productRepository.save(product);

    auditLogService.logAudit(
        AuditAction.DELETE,
        AuditResource.PRODUCT,
        id,
        "Soft deleted product: " + product.getName());

    log.info("Product soft deleted: id={}", id);
  }

  @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "products", allEntries = true)
  public ProductResponse duplicateProduct(@NonNull Long id) {
    Product original = productRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    Product clone = Product.builder()
        .tenantId(original.getTenantId())
        .name(original.getName() + " (Copy)")
        .sku(generateUniqueSku(original.getSku(), original.getTenantId()))
        .barcode(null) // Barcode should be unique
        .categoryId(original.getCategoryId())
        .unit(original.getUnit())
        .purchasePrice(original.getPurchasePrice())
        .salePrice(original.getSalePrice())
        .stock(0) // Reset stock
        .reorderLevel(original.getReorderLevel())
        .isActive(true)
        .build();

    @SuppressWarnings("null")
    Product saved = productRepository.save(clone);
    log.info("Product duplicated: original_id={} new_id={}", id, saved.getId());
    
    auditLogService.logAudit(AuditAction.DUPLICATE_PRODUCT, AuditResource.PRODUCT, saved.getId(), "Duplicated from product #" + id);
    
    return toResponse(saved);
  }

  private String generateUniqueSku(String originalSku, Long tenantId) {
    if (originalSku == null) return null;
    // Remove existing -COPY or -COPY-N suffix to get base
    String baseSku = originalSku.replaceAll("-COPY(-\\d+)?$", "");
    String newSku = baseSku + "-COPY";
    int counter = 1;
    while (productRepository.existsBySkuAndTenantId(newSku, tenantId)) {
        newSku = baseSku + "-COPY-" + counter++;
    }
    return newSku;
  }

  public List<ProductResponse> getLowStockProducts() {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            log.error("Tenant ID is missing in ProductService.getLowStockProducts");
            throw new IllegalStateException("Tenant not resolved from request");
        }
        log.info("TenantContext: {}", tenantId);


    
    return productRepository.findLowStockByTenant(tenantId).stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  @SuppressWarnings("null")
  public List<ProductResponse> getExpiringProducts(Integer days) {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            log.error("Tenant ID is missing in ProductService.getExpiringProducts");
            throw new IllegalStateException("Tenant not resolved from request");
        }
        log.info("TenantContext: {}", tenantId);
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
              .orElse(DEFAULT_REORDER_LEVEL * 3); // 30
    }

    LocalDate threshold = LocalDate.now().plusDays(thresholdDays);
    return pharmacyProductRepository.findExpiring(threshold).stream()
        .map(pp -> toResponseWithPharmacy(pp.getProduct(), pp))
        .collect(Collectors.toList());
  }

  public Page<ProductResponse> searchProducts(String query, Pageable pageable) {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            log.error("Tenant ID is missing in ProductService.searchProducts");
            return Page.empty();
        }
        log.info("TenantContext: {}", tenantId);

    return productRepository.searchFast(tenantId, query, pageable).map(this::toResponse);
  }

  private String getBusinessType() {
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

    // Helper method for safe cache keys
    @SuppressWarnings("unused")
    private String getSafeTenantKey(String suffix) {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant not resolved from request");
        }
        return tenantId + ":" + suffix;
    }

  private Long getTenantId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
                log.error("Tenant extracted: {}", details.getTenantId());
                return details.getTenantId();
            }
        } catch (Exception e) {
            log.trace("Caught expected exception in tenant id retrieval: {}", e.getMessage());
        }
        return null;
    }

  private ProductResponse toResponse(@NonNull Product product) {
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

    // Enrich with pharmacy details if available
    Long productId = product.getId();
    if (productId != null) {
      pharmacyProductRepository
          .findById(productId)
          .ifPresent(
              pp -> {
                builder
                    .batchNumber(pp.getBatchNumber())
                    .expiryDate(pp.getExpiryDate())
                    .manufacturer(pp.getManufacturer())
                    .hsnCode(pp.getHsnCode())
                    .schedule(pp.getSchedule());
              });
    }

    // Enrich with warehouse details if available
    if (productId != null) {
      warehouseProductRepository
          .findById(productId)
          .ifPresent(
              wp -> {
                builder
                    .storageLocation(wp.getStorageLocation())
                    .zone(wp.getZone())
                    .rack(wp.getRack())
                    .bin(wp.getBin());
              });
    }

    return builder.build();
  }

  private ProductResponse toResponse(ProductListView view) {
    return ProductResponse.builder()
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
        .build();
  }

  private ProductResponse toResponseWithPharmacy(@NonNull Product product, @NonNull PharmacyProduct pp) {
    return ProductResponse.builder()
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
        .build();
  }
}
