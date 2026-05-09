package com.ims.product;

import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.platform.repository.TenantRepository;
import com.ims.product.extension.ProductExtensionStrategy;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditResource;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.auth.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = "products", cacheResolver = "tenantAwareCacheResolver")
public class ProductService {

  private final ProductRepository productRepository;
  private final List<ProductExtensionStrategy> extensions;
  private final TenantRepository tenantRepository;
  private final com.ims.shared.audit.AuditLogService auditLogService;

  private static final int DEFAULT_REORDER_LEVEL = 10;
  private static final int MAX_PAGE_SIZE = 100;

  // @PreAuthorize("hasAuthority('view_product')")
  public Page<ProductResponse> getProducts(Pageable pageable) {
    Long tenantId = TenantContext.requireTenantId();

    if (pageable.getPageSize() > MAX_PAGE_SIZE) {
      log.warn(
          "Requested page size {} exceeds limit, capping to {}",
          pageable.getPageSize(),
          MAX_PAGE_SIZE);
      pageable =
          org.springframework.data.domain.PageRequest.of(
              pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    return productRepository.findAllWithDetails(tenantId, pageable).map(this::toResponse);
  }

  @PreAuthorize("hasAuthority('view_product')")
  public List<ProductResponse> getNextProducts(Long lastId, int limit) {
    Long tenantId = TenantContext.requireTenantId();

    Pageable pageable =
        org.springframework.data.domain.PageRequest.of(0, Math.min(limit, MAX_PAGE_SIZE));
    return productRepository.findNextProducts(tenantId, lastId, pageable).stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  @Cacheable(key = "#id")
  @PreAuthorize("hasAuthority('view_product')")
  public ProductResponse getProductById(Long id) {
    Long tenantId = TenantContext.requireTenantId();
    Product product =
        productRepository
            .findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    return toResponse(product);
  }

  public Optional<Product> findByIdWithLock(Long id) {
    Long tenantId = TenantContext.getTenantId();
    return productRepository.findByIdWithLock(id, tenantId);
  }

  @Transactional
  // @PreAuthorize("hasAuthority('create_product')")
  @CacheEvict(allEntries = true)
  public ProductResponse createProduct(CreateProductRequest request) {
    Long tenantId = TenantContext.requireTenantId();

    // PRD 4.1.1 - SKU Normalization (Trim + Uppercase)
    String normalizedSku = request.getSku() != null ? request.getSku().trim().toUpperCase() : null;

    // PRD 4.4 - Check for duplicate SKU using dedicated method
    if (normalizedSku != null && !normalizedSku.isBlank()) {
      if (productRepository.existsBySkuAndTenantId(normalizedSku, tenantId)) {

        throw new IllegalStateException("SKU already exists");
      }
    }

    // Check product limits
    var tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
    if (tenant.getMaxProducts() != null) {
      long currentCount = productRepository.countActive(tenantId);

      if (currentCount >= tenant.getMaxProducts()) {
        throw new IllegalArgumentException(
            "Product limit reached for your plan (" + tenant.getMaxProducts() + ")");
      }
    }

    String businessType = getBusinessType();

    Product product =
        Product.builder()
            .tenantId(tenantId)
            .name(request.getName())
            .sku(normalizedSku)
            .description(request.getDescription())
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
            .isDeleted(false)
            .build();

    product = productRepository.save(product);

    auditLogService.logAudit(
        AuditAction.CREATE,
        AuditResource.PRODUCT,
        product.getId(),
        "Created product: " + product.getName() + " (SKU: " + product.getSku() + ")");

    // Extensions decoupled via Strategy Pattern
    for (ProductExtensionStrategy extension : extensions) {
      if (extension.supports(businessType)) {
        extension.onProductSaved(product, request);
      }
    }

    log.info("Product created: id={} name={}", product.getId(), product.getName());
    return toResponse(product);
  }

  @Transactional
  @PreAuthorize("hasAuthority('update_product')")
  @CacheEvict(key = "#id")
  public ProductResponse updateProduct(Long id, CreateProductRequest request) {
    Product product =
        productRepository
            .findByIdAndTenantIdAndIsDeletedFalse(id, TenantContext.requireTenantId())
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    if (request.getName() != null) {
      product.setName(request.getName());
    }
    if (request.getSku() != null) {
      // PRD 4.1.2 - SKU Normalization and Uniqueness Revalidation
      String normalizedSku = request.getSku().trim().toUpperCase();
      if (!normalizedSku.equals(product.getSku())) {
        if (productRepository.existsBySkuAndTenantId(normalizedSku, product.getTenantId())) {

          throw new IllegalStateException("SKU already exists");
        }
      }
      product.setSku(normalizedSku);
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

    product = productRepository.save(product);

    auditLogService.logAudit(
        AuditAction.UPDATE,
        AuditResource.PRODUCT,
        product.getId(),
        "Updated product: " + product.getName());

    String businessType = getBusinessType();

    // Extensions updates via Strategy Pattern
    for (ProductExtensionStrategy extension : extensions) {
      if (extension.supports(businessType)) {
        extension.onProductSaved(product, request);
      }
    }

    return toResponse(product);
  }

  @Transactional
  @PreAuthorize("hasAuthority('delete_product')")
  @CacheEvict(key = "#id")
  public void deleteProduct(Long id) {
    Product product =
        productRepository
            .findByIdAndTenantIdAndIsDeletedFalse(id, TenantContext.requireTenantId())
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
  @PreAuthorize("hasAuthority('create_product')")
  public ProductResponse duplicateProduct(Long id) {
    Product original =
        productRepository
            .findByIdAndTenantIdAndIsDeletedFalse(id, TenantContext.requireTenantId())
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    Product clone =
        Product.builder()
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

    auditLogService.logAudit(
        AuditAction.DUPLICATE_PRODUCT,
        AuditResource.PRODUCT,
        saved.getId(),
        "Duplicated from product #" + id);

    return toResponse(saved);
  }

  private String generateUniqueSku(String originalSku, Long tenantId) {
    if (originalSku == null) return null;
    String baseSku = originalSku.replaceAll("-COPY(-\\d+)?$", "");
    String newSku = baseSku + "-COPY";
    int counter = 1;
    while (productRepository.existsBySkuAndTenantId(newSku, tenantId)) {
      newSku = baseSku + "-COPY-" + counter++;
    }
    return newSku;
  }

  @PreAuthorize("hasAuthority('view_product')")
  public List<ProductResponse> getLowStockProducts() {
    Long tenantId = getTenantId();
    if (tenantId == null) return Collections.emptyList();

    return productRepository.findLowStock(tenantId).stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  @PreAuthorize("hasAuthority('view_product')")
  public Page<ProductResponse> searchProducts(String query, Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.error("Tenant ID missing for product search");
      return Page.empty();
    }
    return productRepository.searchFast(tenantId, query, pageable).map(this::toResponse);
  }

  private Optional<JwtAuthDetails> getAuthDetails() {
    try {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
        return Optional.of(details);
      }
    } catch (Exception e) {
      log.trace("Failed to retrieve auth details: {}", e.getMessage());
    }
    return Optional.empty();
  }

  private String getBusinessType() {
    return getAuthDetails().map(JwtAuthDetails::getBusinessType).orElse(null);
  }

  private Long getTenantId() {
    return getAuthDetails().map(JwtAuthDetails::getTenantId).orElse(null);
  }

  private ProductResponse toResponse(Product product) {
    ProductResponse.ProductResponseBuilder builder =
        ProductResponse.builder()
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

    // Enrich response via extensions
    for (ProductExtensionStrategy extension : extensions) {
      extension.enrichProductResponse(product, builder);
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
}
