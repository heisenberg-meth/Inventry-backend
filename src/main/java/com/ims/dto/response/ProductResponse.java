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

  @JsonProperty("category_id")
  private Long categoryId;

  private String unit;

  @JsonProperty("purchase_price")
  private BigDecimal purchasePrice;

  @JsonProperty("sale_price")
  private BigDecimal salePrice;

  private Integer stock;

  @JsonProperty("reorder_level")
  private Integer reorderLevel;

  @JsonProperty("is_active")
  private Boolean isActive;

  @JsonProperty("created_at")
  private LocalDateTime createdAt;

  // Pharmacy extension fields
  @JsonProperty("batch_number")
  private String batchNumber;

  @JsonProperty("expiry_date")
  private LocalDate expiryDate;

  private String manufacturer;

  @JsonProperty("hsn_code")
  private String hsnCode;

  private String schedule;

  // Warehouse extension fields
  @JsonProperty("storage_location")
  private String storageLocation;

  private String zone;
  private String rack;
  private String bin;
}
