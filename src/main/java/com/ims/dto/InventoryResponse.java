package com.ims.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    private Long id;
    private Long productId;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private Integer lowStockThreshold;
    private Integer reorderLevel;
    private boolean isLowStock;
    private boolean isBelowReorderLevel;
    private Long version;
}
