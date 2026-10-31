package dev.sozakbay.payledger.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import java.time.Instant;
import java.util.HashMap;
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
    private String traceParent;

    protected OutboxEvent() {
    }

    OutboxEvent(UUID aggregateId, String type, String payload) {
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.traceParent = currentTraceParent();
    }

    private static String currentTraceParent() {
        var headers = new HashMap<String, String>();
        W3CTraceContextPropagator.getInstance().inject(Context.current(), headers, HashMap::put);
        return headers.get("traceparent");
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
    public String getTraceParent() { return traceParent; }
}
