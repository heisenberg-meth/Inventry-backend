package com.ims.product;

import java.time.LocalDate;

/**
 * High-performance projection for stock reporting.
 * Fetches only the minimum required fields to minimize memory and DB overhead.
 */
public interface ProductReportView {
    Long getId();

    String getName();

    String getSku();

    Integer getStock();

    Integer getReorderLevel();

    String getUnit();

    LocalDate getExpiryDate();
}
