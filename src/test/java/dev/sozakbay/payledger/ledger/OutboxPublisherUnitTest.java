package dev.sozakbay.payledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;

class OutboxPublisherUnitTest {

    @Test
    @SuppressWarnings("unchecked")
    void failedSendKeepsEarlierEventsPublishedAndStopsBatch() {
        var sent = event();
        var failed = event();
        var after = event();
        var outbox = mock(OutboxRepository.class);
        when(outbox.lockUnpublishedBatch()).thenReturn(List.of(sent, failed, after));
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(Message.class)))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        new OutboxPublisher(outbox, kafka, OpenTelemetry.noop()).publish();

        assertThat(sent.getPublishedAt()).isNotNull();
        assertThat(failed.getPublishedAt()).isNull();
        assertThat(after.getPublishedAt()).isNull();
    }

    private static OutboxEvent event() {
        var event = new OutboxEvent(UUID.randomUUID(), "TransferCompleted", "{}");
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        return event;
    }
}
