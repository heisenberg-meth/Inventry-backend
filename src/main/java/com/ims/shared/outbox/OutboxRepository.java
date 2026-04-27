package com.ims.shared.outbox;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

  @Query("SELECT e FROM OutboxEvent e WHERE e.processed = false AND e.attempts < 5 ORDER BY e.createdAt ASC")
  List<OutboxEvent> findPendingEvents(Pageable pageable);
}
