package com.ims.shared.exception;

import lombok.Getter;

@Getter
public class InsufficientStockException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final int availableStock;
  private final int requestedQty;

  public InsufficientStockException(String message, int availableStock, int requestedQty) {
    super(message);
    this.availableStock = availableStock;
    this.requestedQty = requestedQty;
  }
}
