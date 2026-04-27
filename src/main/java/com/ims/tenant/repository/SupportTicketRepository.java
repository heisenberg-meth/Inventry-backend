package com.ims.tenant.repository;

import com.ims.model.SupportTicket;
import com.ims.model.SupportTicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.lang.NonNull;

@Repository
public interface SupportTicketRepository
    extends JpaRepository<SupportTicket, Long>, JpaSpecificationExecutor<SupportTicket> {

  @Override
  @NonNull
  <S extends SupportTicket> S save(@NonNull S entity);

  Page<SupportTicket> findByAssignedTo(Long assignedTo, Pageable pageable);

  long countByStatus(SupportTicketStatus status);
}
