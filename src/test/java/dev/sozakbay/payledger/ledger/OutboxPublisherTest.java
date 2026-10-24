package dev.sozakbay.payledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sozakbay.payledger.TestcontainersConfig;
import dev.sozakbay.payledger.events.TransferCompleted;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;

@SpringBootTest
@Import(TestcontainersConfig.class)
class OutboxPublisherTest {

    @Autowired TransferService service;
    @Autowired OutboxRepository outbox;
    @Autowired ConsumerFactory<String, String> consumers;

    @Test
    void publishesTransferCompletedToKafka() {
        var to = service.openAccount("KZT").getId();
        var transfer = service.deposit(UUID.randomUUID().toString(), to, 250);
        var eventId = outbox.findByAggregateId(transfer.getId()).getFirst().getId();

        var props = new Properties();
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = consumers.createConsumer("test-" + UUID.randomUUID(), null, null, props)) {
            consumer.subscribe(List.of(TransferCompleted.TOPIC));
            ConsumerRecord<String, String> found = null;
            for (long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                 found == null && System.nanoTime() < deadline; ) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.key().equals(transfer.getId().toString())) {
                        found = record;
                    }
                }
            }

            assertThat(found).as("TransferCompleted for %s", transfer.getId()).isNotNull();
            assertThat(new String(found.headers().lastHeader("eventId").value(), StandardCharsets.UTF_8))
                    .isEqualTo(eventId.toString());
            assertThat(found.value()).contains(transfer.getId().toString()).containsPattern("\"amount\": ?250");
        }
        assertThat(outbox.findById(eventId).orElseThrow().getPublishedAt()).isNotNull();
    }
}
