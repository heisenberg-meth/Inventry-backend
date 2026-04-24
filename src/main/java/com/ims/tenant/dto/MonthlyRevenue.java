package com.ims.tenant.dto;

import java.math.BigDecimal;

public class MonthlyRevenue {
  private Integer year;
  private Integer month;
  private BigDecimal revenue;

  public MonthlyRevenue(Integer year, Integer month, BigDecimal revenue) {
    this.year = year;
    this.month = month;
    this.revenue = revenue;
  }

  public Integer getYear() {
    return year;
  }

  public void setYear(Integer year) {
    this.year = year;
  }

  public Integer getMonth() {
    return month;
  }

  public void setMonth(Integer month) {
    this.month = month;
  }

  public BigDecimal getRevenue() {
    return revenue;
  }

  public void setRevenue(BigDecimal revenue) {
    this.revenue = revenue;
  }
}
