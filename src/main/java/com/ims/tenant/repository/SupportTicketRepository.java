package com.ims.tenant.repository;

import com.ims.model.SupportTicket;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportTicketRepository
    extends JpaRepository<SupportTicket, Long>, JpaSpecificationExecutor<SupportTicket> {

  Page<SupportTicket> findByTenantId(Long tenantId, Pageable pageable);

  Optional<SupportTicket> findByIdAndTenantId(Long id, Long tenantId);

  Page<SupportTicket> findByAssignedTo(Long assignedTo, Pageable pageable);

  long countByStatus(String status);
}
