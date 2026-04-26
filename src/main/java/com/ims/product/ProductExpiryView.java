package com.ims.product;

import java.time.LocalDate;

/** Optimized projection for expiring pharmacy products. */
public interface ProductExpiryView {
  Long getId();
  String getName();
  String getSku();
  String getBatchNumber();
  LocalDate getExpiryDate();
  String getManufacturer();
}
