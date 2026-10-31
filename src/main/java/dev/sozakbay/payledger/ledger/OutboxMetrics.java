package dev.sozakbay.payledger.ledger;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/** Unpublished outbox size, read at scrape time; the partial index outbox_unpublished keeps the count cheap. */
@Component
class OutboxMetrics implements MeterBinder {

    private final OutboxRepository outbox;

    OutboxMetrics(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("outbox.pending", outbox, OutboxRepository::countByPublishedAtIsNull)
                .description("Outbox events waiting to be published")
                .register(registry);
    }
}
