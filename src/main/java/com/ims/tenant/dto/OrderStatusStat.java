package com.ims.tenant.dto;

import com.ims.model.OrderStatus;

public class OrderStatusStat {

  private OrderStatus status;
  private Long count;

  public OrderStatusStat(OrderStatus status, Long count) {
    this.status = status;
    this.count = count;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public void setStatus(OrderStatus status) {
    this.status = status;
  }

  public Long getCount() {
    return count;
  }

  public void setCount(Long count) {
    this.count = count;
  }
}
