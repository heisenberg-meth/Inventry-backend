package com.ims.tenant.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = false)
public class OrderRequest {
  private Long supplierId;

  private Long customerId;

  private String notes;

  private BigDecimal discountTotal;

  private BigDecimal grandTotal;

  @NotNull(message = "Order items are required")
  @NotEmpty(message = "Order items cannot be empty")
  @Size(max = 100, message = "Cannot exceed 100 items per order")
  @Valid
  private List<OrderItemRequest> items;

  private Long originalOrderId; // for returns
}
