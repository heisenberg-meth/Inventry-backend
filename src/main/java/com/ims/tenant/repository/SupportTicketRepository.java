package com.ims.tenant.repository;

import com.ims.model.SupportTicket;
import com.ims.model.SupportTicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportTicketRepository
    extends JpaRepository<SupportTicket, Long>, JpaSpecificationExecutor<SupportTicket> {

  @Override
  <S extends SupportTicket> S save(S entity);

  Page<SupportTicket> findByAssignedTo(Long assignedTo, Pageable pageable);

  long countByStatus(SupportTicketStatus status);
}
