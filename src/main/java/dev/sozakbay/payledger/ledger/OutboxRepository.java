package dev.sozakbay.payledger.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByAggregateId(UUID aggregateId);

    // SKIP LOCKED lets several app instances publish in parallel without sending the same row twice.
    @Query(value = "select * from outbox where published_at is null order by created_at limit 100 for update skip locked",
            nativeQuery = true)
    List<OutboxEvent> lockUnpublishedBatch();

    @Transactional
    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt < :before")
    int deletePublishedBefore(Instant before);
}
