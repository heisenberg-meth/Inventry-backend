package com.ims.dto.request;

import lombok.Data;

@Data
public class StockInRequest {
    private Long productId;
    private Integer quantity;
    private String notes;
}
