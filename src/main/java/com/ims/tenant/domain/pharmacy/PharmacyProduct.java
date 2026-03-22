package com.ims.tenant.domain.pharmacy;

import com.ims.model.Product;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "pharmacy_products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PharmacyProduct {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "batch_number")
    private String batchNumber;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column
    private String manufacturer;

    @Column(name = "hsn_code")
    private String hsnCode;

    @Column
    private String schedule;
}
