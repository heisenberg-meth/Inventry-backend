package com.ims.tenant.repository;

import com.ims.model.SupportAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportAttachmentRepository extends JpaRepository<SupportAttachment, Long> {

  List<SupportAttachment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
