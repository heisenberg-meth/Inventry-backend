package com.ims.product;

import java.math.BigDecimal;

/** Lightweight projection for CSV export to avoid loading full entities. */
public interface ProductExportView {
  Long getId();
  String getName();
  String getSku();
  Integer getStock();
  BigDecimal getSalePrice();
  Long getCategoryId();
}
