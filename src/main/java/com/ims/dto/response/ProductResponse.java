package com.ims.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
  private Long id;
  private String name;
  private String sku;
  private String barcode;
  private Long categoryId;
  private String unit;
  private BigDecimal purchasePrice;
  private BigDecimal salePrice;
  private Integer stock;
  private Integer reorderLevel;
  private Boolean isActive;
  private LocalDateTime createdAt;

  // Pharmacy extension fields
  private String batchNumber;
  private LocalDate expiryDate;
  private String manufacturer;
  private String hsnCode;
  private String schedule;

  // Warehouse extension fields
  private String storageLocation;
  private String zone;
  private String rack;
  private String bin;
}
