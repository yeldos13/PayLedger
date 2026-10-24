package dev.sozakbay.payledger.ledger;

import dev.sozakbay.payledger.events.TransferCompleted;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
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

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Bean
    static NewTopic transfersTopic() {
        return new NewTopic(TransferCompleted.TOPIC, 3, (short) 1);
    }

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void publish() throws Exception {
        for (var event : outbox.lockUnpublishedBatch()) {
            var message = MessageBuilder.withPayload(event.getPayload())
                    .setHeader(KafkaHeaders.TOPIC, TransferCompleted.TOPIC)
                    // Keyed by transfer id; all events of one transfer land in one partition, in order.
                    .setHeader(KafkaHeaders.KEY, event.getAggregateId().toString())
                    .setHeader("eventId", event.getId().toString())
                    .setHeader("eventType", event.getType())
                    .build();
            kafka.send(message).get(10, TimeUnit.SECONDS);
            event.markPublished();
        }
    }
}
