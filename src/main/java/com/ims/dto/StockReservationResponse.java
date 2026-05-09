package com.ims.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationResponse {
  private Long productId;
  private Integer reservedQuantity;
  private Integer availableAfterReservation;
  private String reservationId;
}
