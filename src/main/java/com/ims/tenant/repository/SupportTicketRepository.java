package com.ims.tenant.repository;

import com.ims.model.SupportTicket;
import com.ims.model.SupportTicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import org.springframework.data.repository.query.Param;

@Repository
public interface SupportTicketRepository
    extends JpaRepository<SupportTicket, Long>, JpaSpecificationExecutor<SupportTicket> {

  @Override
  <S extends SupportTicket> S save(S entity);

  Page<SupportTicket> findByAssignedTo(Long assignedTo, Pageable pageable);

  @Query("SELECT t FROM SupportTicket t WHERE t.tenantId = :tenantId")
  Page<SupportTicket> findAllByTenantId(@Param("tenantId") Long tenantId,
      Pageable pageable);

  long countByStatus(SupportTicketStatus status);

  @Query("SELECT t FROM SupportTicket t WHERE t.id = :id AND t.tenantId = :tenantId")
  Optional<SupportTicket> findByIdAndTenantId(
      @Param("id") Long id,
      @Param("tenantId") Long tenantId);
}
