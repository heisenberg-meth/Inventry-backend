package com.ims.tenant.dto;

import java.math.BigDecimal;

/**
 * Interface-based projection for monthly revenue reports.
 * Used by OrderRepository to aggregate sales data without loading full
 * entities.
 */
public interface MonthlyRevenue {
  Integer getYear();

  Integer getMonth();

  BigDecimal getRevenue();
}
