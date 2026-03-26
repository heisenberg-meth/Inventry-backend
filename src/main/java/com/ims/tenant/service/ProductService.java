package com.ims.tenant.service;

import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.model.Product;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.tenant.domain.pharmacy.PharmacyProduct;
import com.ims.tenant.domain.pharmacy.PharmacyProductRepository;
import com.ims.tenant.domain.warehouse.WarehouseProduct;
import com.ims.tenant.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

  private static final int DEFAULT_REORDER_LEVEL = 10;

  @Cacheable(cacheResolver = "tenantAwareCacheResolver", value = "products", key = "'list:' + (#pageable?.pageNumber ?: 0) + ':' + (#pageable?.pageSize ?: 10)")
  public Page<ProductResponse> getProducts(Long tenantId, Pageable pageable) {
    return productRepository.findByIsActiveTrue(pageable).map(this::toResponse);
  }

  public ProductResponse getProductById(Long id) {
    Product product =
        productRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    return toResponse(product);
  }

  @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "products", allEntries = true)
  public ProductResponse createProduct(CreateProductRequest request) {
    String businessType = getBusinessType();

    // Validate pharmacy products must have pharmacy_details
    if ("PHARMACY".equals(businessType) && request.getPharmacyDetails() == null) {
      throw new IllegalArgumentException("Pharmacy products require pharmacy_details");
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
  public ProductResponse updateProduct(Long id, CreateProductRequest request) {
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
    return toResponse(product);
  }

  @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "products", allEntries = true)
  public void deleteProduct(Long id) {
    Product product =
        productRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    product.setIsActive(false);
    product.setUpdatedAt(LocalDateTime.now());
    productRepository.save(product);
    log.info("Product soft deleted: id={}", id);
  }

  public List<ProductResponse> getLowStockProducts() {
    return productRepository.findLowStock().stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  public List<ProductResponse> getExpiringProducts(int days) {
    String businessType = getBusinessType();

    if (!"PHARMACY".equals(businessType)) {
      throw new IllegalArgumentException(
          "Expiring products endpoint is only available for PHARMACY tenants");
    }

    LocalDate threshold = LocalDate.now().plusDays(days);
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

  private ProductResponse toResponse(Product product) {
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
    pharmacyProductRepository
        .findById(product.getId())
        .ifPresent(
            pp -> {
              builder
                  .batchNumber(pp.getBatchNumber())
                  .expiryDate(pp.getExpiryDate())
                  .manufacturer(pp.getManufacturer())
                  .hsnCode(pp.getHsnCode());
            });

    // Enrich with warehouse details if available
    warehouseProductRepository
        .findById(product.getId())
        .ifPresent(
            wp -> {
              builder
                  .storageLocation(wp.getStorageLocation())
                  .zone(wp.getZone())
                  .rack(wp.getRack())
                  .bin(wp.getBin());
            });

    return builder.build();
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
        .isActive(product.getIsActive())
        .createdAt(product.getCreatedAt())
        .batchNumber(pp.getBatchNumber())
        .expiryDate(pp.getExpiryDate())
        .manufacturer(pp.getManufacturer())
        .hsnCode(pp.getHsnCode())
        .build();
  }
}
