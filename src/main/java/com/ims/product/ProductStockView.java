package com.ims.product;

/** Focused projection for stock levels to avoid fetching full product details. */
public interface ProductStockView {
  Long getId();
  String getName();
  Integer getStock();
}
