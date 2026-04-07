package com.ims.tenant.service;

import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.model.Product;
import com.ims.model.Tenant;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.rbac.RequiresPermission;
import com.ims.tenant.domain.pharmacy.PharmacyProduct;
import com.ims.tenant.domain.pharmacy.PharmacyProductRepository;
import com.ims.tenant.domain.warehouse.WarehouseProduct;
import com.ims.tenant.repository.ProductRepository;
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
@SuppressWarnings("null")
public class ProductService {

  private final ProductRepository productRepository;
  private final PharmacyProductRepository pharmacyProductRepository;
  private final WarehouseProductRepository warehouseProductRepository;
  private final TenantRepository tenantRepository;
  private final SystemConfigService systemConfigService;
  private final com.ims.shared.audit.AuditLogService auditLogService;

  private static final int DEFAULT_REORDER_LEVEL = 10;

  @Cacheable(cacheResolver = "tenantAwareCacheResolver", value = "products", key = "'list:' + (#pageable?.pageNumber ?: 0) + ':' + (#pageable?.pageSize ?: 10)")
  public Page<ProductResponse> getProducts(Long tenantId, Pageable pageable) {
    return productRepository.findByIsActiveTrue(pageable).map(this::toResponse);
  }

  public ProductResponse getProductById(@NonNull Long id) {
    Product product =
        productRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    return toResponse(product);
  }

  @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "products", allEntries = true)
  public ProductResponse createProduct(CreateProductRequest request) {
    Long tenantId = getTenantId();
    if (tenantId != null) {
      var tenant =
          tenantRepository
              .findById(tenantId)
              .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
      if (tenant.getMaxProducts() != null) {
        long currentCount = productRepository.countActive();
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
        "CREATE",
        "PRODUCT",
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
        "UPDATE",
        "PRODUCT",
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
        "DELETE",
        "PRODUCT",
        id,
        "Soft deleted product: " + product.getName());

    log.info("Product soft deleted: id={}", id);
  }

  public List<ProductResponse> getLowStockProducts() {
    return productRepository.findLowStock().stream()
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
    return productRepository.searchProducts(query, pageable).map(this::toResponse);
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
