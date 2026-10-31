package dev.sozakbay.payledger.ledger;

import dev.sozakbay.payledger.events.TransferCompleted;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relays committed outbox rows to Kafka. Delivery is at least once: if the process dies after send but
 * before commit, the batch is sent again, so consumers dedupe by the eventId header.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    static final Duration RETENTION = Duration.ofDays(7);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Tracer tracer;

    public OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka, OpenTelemetry openTelemetry) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.tracer = openTelemetry.getTracer("payledger");
    }

    @Bean
    static NewTopic transfersTopic() {
        return new NewTopic(TransferCompleted.TOPIC, 3, (short) 1);
    }

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void publish() {
        for (var event : outbox.lockUnpublishedBatch()) {
            // The publish span continues the trace of the request that wrote the event, not of this poll.
            var span = tracer.spanBuilder("PublishTransferEvent")
                    .setSpanKind(SpanKind.PRODUCER)
                    .setParent(parentOf(event))
                    .setAttribute("messaging.system", "kafka")
                    .setAttribute("messaging.message.id", event.getId().toString())
                    .startSpan();
            var message = MessageBuilder.withPayload(event.getPayload())
                    .setHeader(KafkaHeaders.TOPIC, TransferCompleted.TOPIC)
                    // Keyed by transfer id; all events of one transfer land in one partition, in order.
                    .setHeader(KafkaHeaders.KEY, event.getAggregateId().toString())
                    .setHeader("eventId", event.getId().toString())
                    .setHeader("eventType", event.getType());
            W3CTraceContextPropagator.getInstance().inject(Context.root().with(span), message, (m, key, value) -> m.setHeader(key, value));
            try {
                kafka.send(message.build()).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                span.recordException(e);
                span.setStatus(StatusCode.ERROR);
                // Keep what was already sent marked as published, stop here to preserve order, retry next poll.
                log.atWarn().addKeyValue("eventId", event.getId()).setCause(e).log("Outbox publish failed, will retry");
                return;
            } finally {
                span.end();
            }
            event.markPublished();
        }
    }

    private static Context parentOf(OutboxEvent event) {
        return event.getTraceParent() == null ? Context.root()
                : W3CTraceContextPropagator.getInstance().extract(Context.root(), event.getTraceParent(), TRACEPARENT);
    }

    private static final TextMapGetter<String> TRACEPARENT = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(String carrier) {
            return List.of("traceparent");
        }

        @Override
        public String get(String carrier, String key) {
            return "traceparent".equals(key) ? carrier : null;
        }
    };

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void deletePublished() {
        var deleted = outbox.deletePublishedBefore(Instant.now().minus(RETENTION));
        if (deleted > 0) {
            log.atInfo().addKeyValue("deleted", deleted).log("Deleted published outbox events");
        }
    }
}
