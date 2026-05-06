package com.ims.product.extension;

import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.product.Product;

/**
 * Strategy interface for industry-specific product extensions.
 * Allows decoupling specific business logic (e.g., Pharmacy, Warehouse)
 * from the core ProductService.
 */
public interface ProductExtensionStrategy {

  /**
   * Checks if this extension supports the given business type.
   */
  boolean supports(String businessType);

  /**
   * Called after a core product has been saved.
   */
  void onProductSaved(Product product, CreateProductRequest request);

  /**
   * Enriches the product response with extension-specific details.
   */
  void enrichProductResponse(Product product, ProductResponse.ProductResponseBuilder builder);
}
