package com.ims.product;

import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditResource;

import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.model.Tenant;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.domain.pharmacy.PharmacyProduct;
import com.ims.tenant.domain.pharmacy.PharmacyProductRepository;
import com.ims.tenant.domain.warehouse.WarehouseProduct;
import com.ims.tenant.service.WarehouseProductRepository;
import com.ims.platform.repository.TenantRepository;
import com.ims.platform.service.SystemConfigService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  @Cacheable(cacheResolver = "tenantAwareCacheResolver", value = "products", key = "'list:' + (#pageable?.pageNumber ?: 0) + ':' + (#pageable?.pageSize ?: 10) + ':' + (#pageable?.sort?.toString() ?: '')")
  public Page<ProductResponse> getProducts(Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.error("Tenant ID is missing in ProductService.getProducts");
      throw new IllegalStateException("Missing tenant context");
    }

    if (pageable.getPageSize() > MAX_PAGE_SIZE) {
      log.warn("Requested page size {} exceeds limit, capping to {}", pageable.getPageSize(), MAX_PAGE_SIZE);
      pageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE,
          pageable.getSort());
    }

    return productRepository.findAllWithDetails(tenantId, pageable).map(this::toResponse);
  }

  public List<ProductResponse> getNextProducts(Long lastId, int limit) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Missing tenant context");
    }
    Pageable pageable = org.springframework.data.domain.PageRequest.of(0, Math.min(limit, MAX_PAGE_SIZE));
    return productRepository.findNextProducts(tenantId, lastId, pageable).stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  public ProductResponse getProductById(Long id) {
    Product product = productRepository
        .findById(id)
        .filter(p -> !p.getIsDeleted())
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    return toResponse(product);
  }

  public java.util.Optional<Product> findByIdWithLock(Long id) {
    Long tenantId = TenantContext.getTenantId();
    return productRepository.findByIdWithLockAndTenantId(id, tenantId);
  }

  @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "products", allEntries = true)
  public ProductResponse createProduct(CreateProductRequest request) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("TenantContext not set - cannot create product");
    }

    // PRD 4.4 - Check for duplicate SKU using dedicated method
    if (request.getSku() != null && !request.getSku().isBlank()) {
      if (productRepository.existsByTenantIdAndSku(tenantId, request.getSku())) {
        throw new IllegalStateException("SKU already exists");
      }
    }

    // Check product limits
    var tenant = tenantRepository
        .findById(tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
    if (tenant.getMaxProducts() != null) {
      long currentCount = productRepository.countActiveByTenant(tenantId);
      if (currentCount >= tenant.getMaxProducts()) {
        throw new IllegalArgumentException(
            "Product limit reached for your plan (" + tenant.getMaxProducts() + ")");
      }
    }

    String businessType = getBusinessType();

    if ("PHARMACY".equals(businessType)) {
      if (!systemConfigService.isPharmacyEnabled()) {
        throw new IllegalStateException("Pharmacy extension is currently disabled globally");
      }
      if (request.getPharmacyDetails() == null) {
        throw new IllegalArgumentException("Pharmacy products require pharmacy_details");
      }
    }

    Product product = Product.builder()
        .tenantId(tenantId)
        .name(request.getName())
        .sku(request.getSku())
        .description(request.getDescription())
        .barcode(request.getBarcode())
        .categoryId(request.getCategoryId())
        .unit(request.getUnit())
        .purchasePrice(request.getPurchasePrice())
        .salePrice(request.getSalePrice())
        .reorderLevel(
            request.getReorderLevel() != null ? request.getReorderLevel() : DEFAULT_REORDER_LEVEL)
        .stock(0)
        .isDeleted(false)
        .build();

    try {
      product = productRepository.save(product);
    } catch (DataIntegrityViolationException e) {
      // PRD 4.6 - Concurrency & Data Integrity
      throw new IllegalStateException("SKU already exists");
    }

    auditLogService.logAudit(
        AuditAction.CREATE,
        AuditResource.PRODUCT,
        product.getId(),
        "Created product: " + product.getName() + " (SKU: " + product.getSku() + ")");

    // Extensions
    if ("PHARMACY".equals(businessType) && request.getPharmacyDetails() != null) {
      var pd = request.getPharmacyDetails();
      PharmacyProduct pp = PharmacyProduct.builder()
          .product(product)
          .batchNumber(pd.getBatchNumber())
          .expiryDate(LocalDate.parse(pd.getExpiryDate()))
          .manufacturer(pd.getManufacturer())
          .hsnCode(pd.getHsnCode())
          .schedule(pd.getSchedule())
          .build();
      pharmacyProductRepository.save(pp);
    }

    if ("WAREHOUSE".equals(businessType) && request.getWarehouseDetails() != null) {
      var wd = request.getWarehouseDetails();
      WarehouseProduct wp = WarehouseProduct.builder()
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

  @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "products", allEntries = true)
  public ProductResponse updateProduct(Long id, CreateProductRequest request) {
    Product product = productRepository
        .findById(id)
        .filter(p -> !p.getIsDeleted())
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    if (request.getName() != null) {
      product.setName(request.getName());
    }
    if (request.getSku() != null) {
      // Check uniqueness if SKU is changing
      if (!request.getSku().equals(product.getSku())) {
        if (productRepository.existsByTenantIdAndSku(product.getTenantId(), request.getSku())) {
          throw new IllegalStateException("SKU already exists");
        }
      }
      product.setSku(request.getSku());
    }
    if (request.getDescription() != null) {
      product.setDescription(request.getDescription());
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

    try {
      product = productRepository.save(product);
    } catch (DataIntegrityViolationException e) {
      throw new IllegalStateException("SKU already exists");
    }

    auditLogService.logAudit(
        AuditAction.UPDATE,
        AuditResource.PRODUCT,
        product.getId(),
        "Updated product: " + product.getName());

    String businessType = getBusinessType();

    // Extensions updates
    if ("PHARMACY".equals(businessType) && request.getPharmacyDetails() != null) {
      var pd = request.getPharmacyDetails();
      PharmacyProduct pp = pharmacyProductRepository
          .findById(product.getId())
          .orElse(PharmacyProduct.builder().product(product).build());

      if (pd.getBatchNumber() != null)
        pp.setBatchNumber(pd.getBatchNumber());
      if (pd.getExpiryDate() != null)
        pp.setExpiryDate(LocalDate.parse(pd.getExpiryDate()));
      if (pd.getManufacturer() != null)
        pp.setManufacturer(pd.getManufacturer());
      if (pd.getHsnCode() != null)
        pp.setHsnCode(pd.getHsnCode());
      if (pd.getSchedule() != null)
        pp.setSchedule(pd.getSchedule());

      pharmacyProductRepository.save(pp);
    }

    if ("WAREHOUSE".equals(businessType) && request.getWarehouseDetails() != null) {
      var wd = request.getWarehouseDetails();
      WarehouseProduct wp = warehouseProductRepository
          .findById(product.getId())
          .orElse(WarehouseProduct.builder().product(product).build());

      if (wd.getStorageLocation() != null)
        wp.setStorageLocation(wd.getStorageLocation());
      if (wd.getZone() != null)
        wp.setZone(wd.getZone());
      if (wd.getRack() != null)
        wp.setRack(wd.getRack());
      if (wd.getBin() != null)
        wp.setBin(wd.getBin());

      warehouseProductRepository.save(wp);
    }

    return toResponse(product);
  }

  @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "products", allEntries = true)
  @PreAuthorize("hasAuthority('delete_product')")
  public void deleteProduct(Long id) {
    Product product = productRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    // PRD 4.1.3 Soft Delete
    product.setIsDeleted(true);
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
  public ProductResponse duplicateProduct(Long id) {
    Product original = productRepository.findById(id)
        .filter(p -> !p.getIsDeleted())
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    Product clone = Product.builder()
        .tenantId(original.getTenantId())
        .name(original.getName() + " (Copy)")
        .sku(generateUniqueSku(original.getSku(), original.getTenantId()))
        .description(original.getDescription())
        .barcode(null)
        .categoryId(original.getCategoryId())
        .unit(original.getUnit())
        .purchasePrice(original.getPurchasePrice())
        .salePrice(original.getSalePrice())
        .stock(0)
        .reorderLevel(original.getReorderLevel())
        .isDeleted(false)
        .build();

    Product saved = productRepository.save(clone);
    log.info("Product duplicated: original_id={} new_id={}", id, saved.getId());

    auditLogService.logAudit(AuditAction.DUPLICATE_PRODUCT, AuditResource.PRODUCT, saved.getId(),
        "Duplicated from product #" + id);

    return toResponse(saved);
  }

  private String generateUniqueSku(String originalSku, Long tenantId) {
    if (originalSku == null)
      return null;
    String baseSku = originalSku.replaceAll("-COPY(-\\d+)?$", "");
    String newSku = baseSku + "-COPY";
    int counter = 1;
    while (productRepository.existsByTenantIdAndSku(tenantId, newSku)) {
      newSku = baseSku + "-COPY-" + counter++;
    }
    return newSku;
  }

  public List<ProductResponse> getLowStockProducts() {
    Long tenantId = getTenantId();
    if (tenantId == null)
      return java.util.Collections.emptyList();

    return productRepository.findLowStockByTenant(tenantId).stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  public List<ProductResponse> getExpiringProducts(Integer days) {
    String businessType = getBusinessType();

    if (!"PHARMACY".equals(businessType)) {
      throw new IllegalArgumentException(
          "Expiring products endpoint is only available for PHARMACY tenants");
    }

    int thresholdDays;
    if (days != null && days > 0) {
      thresholdDays = days;
    } else {
      Long tenantId = getTenantId();
      thresholdDays = tenantRepository
          .findById(tenantId)
          .map(Tenant::getExpiryThresholdDays)
          .orElse(DEFAULT_REORDER_LEVEL * 3);
    }

    LocalDate threshold = LocalDate.now().plusDays(thresholdDays);
    return pharmacyProductRepository.findExpiring(threshold).stream()
        .map(pp -> toResponseWithPharmacy(pp.getProduct(), pp))
        .collect(Collectors.toList());
  }

  public Page<ProductResponse> searchProducts(String query, Pageable pageable) {
    Long tenantId = getTenantId();
    if (tenantId == null)
      return Page.empty();
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

  private Long getTenantId() {
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

  private ProductResponse toResponse(Product product) {
    ProductResponse.ProductResponseBuilder builder = ProductResponse.builder()
        .id(product.getId())
        .name(product.getName())
        .sku(product.getSku())
        .description(product.getDescription())
        .barcode(product.getBarcode())
        .categoryId(product.getCategoryId())
        .unit(product.getUnit())
        .purchasePrice(product.getPurchasePrice())
        .salePrice(product.getSalePrice())
        .stock(product.getStock())
        .reorderLevel(product.getReorderLevel())
        .isDeleted(product.getIsDeleted())
        .createdAt(product.getCreatedAt());

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
        .isDeleted(view.getIsDeleted())
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

  private ProductResponse toResponseWithPharmacy(Product product, PharmacyProduct pp) {
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
        .isDeleted(product.getIsDeleted())
        .createdAt(product.getCreatedAt())
        .batchNumber(pp.getBatchNumber())
        .expiryDate(pp.getExpiryDate())
        .manufacturer(pp.getManufacturer())
        .hsnCode(pp.getHsnCode())
        .schedule(pp.getSchedule())
        .build();
  }
}
