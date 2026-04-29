package com.ims.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CreateProductRequest {
  @NotBlank(message = "Product name is required")
  @Size(max = 255)
  private String name;

  @Size(max = 100)
  private String sku;

  @Size(max = 100)
  private String barcode;

  private Long categoryId;

  @Size(max = 50)
  private String unit;

  @Positive(message = "Purchase price must be positive")
  private BigDecimal purchasePrice;

  @NotNull(message = "Sale price is required")
  @Positive(message = "Sale price must be positive")
  private BigDecimal salePrice;
  private Integer reorderLevel;

  @Valid
  private PharmacyDetailsRequest pharmacyDetails;

  private WarehouseDetailsRequest warehouseDetails;

  @Data
  public static class PharmacyDetailsRequest {
    private String batchNumber;

    @NotNull(message = "Expiry date is required for pharmacy products")
    private String expiryDate;
    private String manufacturer;
    private String hsnCode;
    private String schedule;
  }

  @Data
  public static class WarehouseDetailsRequest {
    private String storageLocation;
    private String zone;
    private String rack;
    private String bin;
  }
}
