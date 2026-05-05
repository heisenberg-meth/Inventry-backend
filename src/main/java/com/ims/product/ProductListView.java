package com.ims.product;

import java.math.BigDecimal;

/**
 * Optimized projection for product listing to reduce DB load and serialization
 * cost.
 */
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

    Boolean getIsDeleted();

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

    /**
     * Helper to bridge isDeleted to status string.
     */
    default String getStatus() {
        return Boolean.TRUE.equals(getIsDeleted()) ? "DELETED" : "ACTIVE";
    }
}
