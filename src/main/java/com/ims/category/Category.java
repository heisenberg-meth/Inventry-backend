package com.ims.category;

import java.math.BigDecimal;
import com.ims.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Category extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @Column
  private String description;

  @Column(name = "tax_rate")
  @Builder.Default
  private BigDecimal taxRate = BigDecimal.ZERO;
}
