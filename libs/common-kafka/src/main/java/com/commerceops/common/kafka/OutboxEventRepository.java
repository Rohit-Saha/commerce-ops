package com.commerceops.common.kafka;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = "SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY id ASC LIMIT 50", nativeQuery = true)
    List<OutboxEvent> findPendingBatch();
}
