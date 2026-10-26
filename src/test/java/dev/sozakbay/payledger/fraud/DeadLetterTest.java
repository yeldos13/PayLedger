package dev.sozakbay.payledger.fraud;

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
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;

@SpringBootTest
@Import(TestcontainersConfig.class)
class DeadLetterTest {

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ConsumerFactory<String, String> consumers;
    @Autowired JdbcTemplate jdbc;

    @Test
    void malformedEventGoesToDeadLetterTopic() throws Exception {
        var key = "poison-" + UUID.randomUUID();
        var eventId = UUID.randomUUID().toString();
        var record = new ProducerRecord<String, String>(TransferCompleted.TOPIC, key, "{not json");
        record.headers().add("eventId", eventId.getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();

        var props = new Properties();
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        ConsumerRecord<String, String> dead = null;
        try (var consumer = consumers.createConsumer("test-" + UUID.randomUUID(), null, null, props)) {
            consumer.subscribe(List.of(FraudKafkaConfig.DLT));
            for (long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                 dead == null && System.nanoTime() < deadline; ) {
                for (var r : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(r.key())) {
                        dead = r;
                    }
                }
            }
        }

        assertThat(dead).as("dead letter for %s", key).isNotNull();
        assertThat(dead.value()).isEqualTo("{not json");
        assertThat(dead.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
        // The failed attempt rolled back, so the event is not marked as processed.
        assertThat(jdbc.queryForObject("select count(*) from fraud_processed_events where event_id = ?::uuid",
                Long.class, eventId)).isZero();
    }
}
