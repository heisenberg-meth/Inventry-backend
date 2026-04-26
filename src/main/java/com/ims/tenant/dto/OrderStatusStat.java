package com.ims.tenant.dto;

import com.ims.model.OrderStatus;

/**
 * Interface-based projection for order status statistics.
 */
public interface OrderStatusStat {
  OrderStatus getStatus();
  Long getCount();
}
