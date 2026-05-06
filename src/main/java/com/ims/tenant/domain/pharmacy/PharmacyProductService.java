package com.ims.tenant.domain.pharmacy;

import com.ims.dto.response.ProductResponse;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.product.Product;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.auth.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PharmacyProductService {

  private final PharmacyProductRepository pharmacyProductRepository;
  private final TenantRepository tenantRepository;

  @Transactional(readOnly = true)
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
      Long tenantId = TenantContext.getTenantId();
      thresholdDays = tenantRepository
          .findById(tenantId)
          .map(Tenant::getExpiryThresholdDays)
          .orElse(30); // Default to 30 days
    }

    LocalDate threshold = LocalDate.now().plusDays(thresholdDays);
    return pharmacyProductRepository.findExpiring(threshold).stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  private String getBusinessType() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof JwtAuthDetails details) {
      return details.getBusinessType();
    }
    return null;
  }

  private ProductResponse toResponse(PharmacyProduct pp) {
    Product product = pp.getProduct();
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
