package com.ims.tenant.domain.warehouse;

import com.ims.model.Product;
import jakarta.persistence.*;
import lombok.*;

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

    @Column
    private String zone;

    @Column
    private String rack;

    @Column
    private String bin;
}
