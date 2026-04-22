package com.ims.tenant.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OrderRequest {
    private Long supplierId;
    private Long customerId;
    private String notes;
    private BigDecimal discountTotal;
    private BigDecimal grandTotal;

    @NotEmpty(message = "Order items cannot be empty")
    @Valid
    private List<OrderItemRequest> items;

    private Long originalOrderId; // for returns
}
