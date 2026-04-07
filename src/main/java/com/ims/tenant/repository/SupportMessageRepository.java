package com.ims.tenant.repository;

import com.ims.model.SupportMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

  List<SupportMessage> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
