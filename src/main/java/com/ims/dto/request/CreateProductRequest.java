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

  @org.springframework.lang.Nullable
  @Size(max = 100)
  private String sku;

  @org.springframework.lang.Nullable
  @Size(max = 100)
  private String barcode;

  @org.springframework.lang.Nullable
  private Long categoryId;

  @org.springframework.lang.Nullable
  @Size(max = 50)
  private String unit;

  @org.springframework.lang.Nullable
  @Positive(message = "Purchase price must be positive")
  private BigDecimal purchasePrice;

  @NotNull(message = "Sale price is required")
  @Positive(message = "Sale price must be positive")
  private BigDecimal salePrice;

  @org.springframework.lang.Nullable
  private Integer reorderLevel;

  @org.springframework.lang.Nullable
  @Valid
  private PharmacyDetailsRequest pharmacyDetails;

  @org.springframework.lang.Nullable
  private WarehouseDetailsRequest warehouseDetails;

  @Data
  public static class PharmacyDetailsRequest {
    @org.springframework.lang.Nullable
    private String batchNumber;

    @NotNull(message = "Expiry date is required for pharmacy products")
    private String expiryDate;

    @org.springframework.lang.Nullable
    private String manufacturer;

    @org.springframework.lang.Nullable
    private String hsnCode;

    @org.springframework.lang.Nullable
    private String schedule;
  }

  @Data
  public static class WarehouseDetailsRequest {
    @org.springframework.lang.Nullable
    private String storageLocation;

    @org.springframework.lang.Nullable
    private String zone;

    @org.springframework.lang.Nullable
    private String rack;

    @org.springframework.lang.Nullable
    private String bin;
  }
}
