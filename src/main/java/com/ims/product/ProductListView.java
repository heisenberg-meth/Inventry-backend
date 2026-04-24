package com.ims.product;

import java.math.BigDecimal;

/** Optimized projection for product listing to reduce DB load and serialization cost. */
public interface ProductListView {
  Long getId();

  String getName();

  String getSku();

  String getBarcode();

  Long getCategoryId();

  String getUnit();

  BigDecimal getPurchasePrice();

  BigDecimal getSalePrice();

  Integer getStock();

  Integer getReorderLevel();

  Boolean getIsActive();

  java.time.LocalDateTime getCreatedAt();

  // Pharmacy fields
  String getBatchNumber();

  java.time.LocalDate getExpiryDate();

  String getManufacturer();

  String getHsnCode();

  String getSchedule();

  // Warehouse fields
  String getStorageLocation();

  String getZone();

  String getRack();

  String getBin();

  /** Helper to bridge isActive to status string until schema is unified. */
  default String getStatus() {
    return Boolean.TRUE.equals(getIsActive()) ? "ACTIVE" : "INACTIVE";
  }
}
