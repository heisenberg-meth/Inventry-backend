package com.ims.tenant.domain.warehouse;

import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.product.Product;
import com.ims.product.extension.ProductExtensionStrategy;
import com.ims.tenant.service.WarehouseProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WarehouseProductExtension implements ProductExtensionStrategy {

  private final WarehouseProductRepository warehouseProductRepository;

  @Override
  public boolean supports(String businessType) {
    return "WAREHOUSE".equals(businessType);
  }

  @Override
  public void onProductSaved(Product product, CreateProductRequest request) {
    if (request.getWarehouseDetails() != null) {
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
  }

  @Override
  public void enrichProductResponse(Product product, ProductResponse.ProductResponseBuilder builder) {
    warehouseProductRepository.findById(product.getId())
        .ifPresent(wp -> {
          builder
              .storageLocation(wp.getStorageLocation())
              .zone(wp.getZone())
              .rack(wp.getRack())
              .bin(wp.getBin());
        });
  }
}
