package com.ims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "customers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Customer extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @Column
  private String phone;

  @Column
  private String email;

  @Column
  private String address;

  @Column
  private String gstin;

  @Column(name = "is_deleted")
  @Builder.Default
  private Boolean isDeleted = false;
}
