package com.ims.tenant.domain.pharmacy;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PharmacyProductRepository extends JpaRepository<PharmacyProduct, Long> {
  List<PharmacyProduct> findByExpiryDateBefore(LocalDate date);

  @Query(
      "SELECT pp FROM PharmacyProduct pp JOIN pp.product p "
          + "WHERE pp.expiryDate <= :date AND p.isActive = true")
  List<PharmacyProduct> findExpiring(@Param("date") LocalDate date);

  @Query(
      "SELECT COUNT(pp) FROM PharmacyProduct pp JOIN pp.product p "
          + "WHERE pp.expiryDate <= :date AND p.isActive = true")
  long countExpiring(@Param("date") LocalDate date);
}
