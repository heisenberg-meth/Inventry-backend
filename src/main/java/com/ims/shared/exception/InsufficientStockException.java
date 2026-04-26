package com.ims.shared.exception;

import lombok.Getter;

@Getter
public class InsufficientStockException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final int availableStock;
  private final int requestedQuantity;

  public InsufficientStockException(String message, int availableStock, int requestedQuantity) {
    super(message);
    this.availableStock = availableStock;
    this.requestedQuantity = requestedQuantity;
  }
}
