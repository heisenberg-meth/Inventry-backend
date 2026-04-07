package com.ims.tenant.domain.warehouse;

import com.ims.model.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "warehouse_products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseProduct {

  @Id
  @Column(name = "product_id")
  private Long productId;

  @OneToOne
  @MapsId
  @JoinColumn(name = "product_id")
  private Product product;

  @Column(name = "storage_location")
  private String storageLocation;

  @Column private String zone;

  @Column private String rack;

  @Column private String bin;
}
