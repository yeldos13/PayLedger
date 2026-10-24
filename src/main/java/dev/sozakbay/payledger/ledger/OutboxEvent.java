package dev.sozakbay.payledger.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox")
public class OutboxEvent {

    @Id
    @GeneratedValue
    private UUID id;
    private UUID aggregateId;
    private String type;
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;
    private Instant createdAt;
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    OutboxEvent(UUID aggregateId, String type, String payload) {
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    void markPublished() {
        publishedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
