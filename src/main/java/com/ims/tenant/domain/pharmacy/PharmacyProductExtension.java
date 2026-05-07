package com.ims.tenant.domain.pharmacy;

import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.product.Product;
import com.ims.product.extension.ProductExtensionStrategy;
import com.ims.platform.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
public class PharmacyProductExtension implements ProductExtensionStrategy {

  private final PharmacyProductRepository pharmacyProductRepository;
  private final SystemConfigService systemConfigService;

  @Override
  public boolean supports(String businessType) {
    return "PHARMACY".equals(businessType);
  }

  @Override
  public void onProductSaved(Product product, CreateProductRequest request) {
    if (!systemConfigService.isPharmacyEnabled()) {
      throw new IllegalStateException("Pharmacy extension is currently disabled globally");
    }

    if (request.getPharmacyDetails() != null) {
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
    } else if (product.getId() == null) {
      // For new products, pharmacy details are mandatory if it's a pharmacy business
      // This logic was in createProduct
      throw new IllegalArgumentException("Pharmacy products require pharmacy_details");
    }
  }

  @Override
  public void enrichProductResponse(Product product, ProductResponse.ProductResponseBuilder builder) {
    pharmacyProductRepository.findById(product.getId())
        .ifPresent(pp -> {
          builder
              .batchNumber(pp.getBatchNumber())
              .expiryDate(pp.getExpiryDate())
              .manufacturer(pp.getManufacturer())
              .hsnCode(pp.getHsnCode())
              .schedule(pp.getSchedule());
        });
  }
}
