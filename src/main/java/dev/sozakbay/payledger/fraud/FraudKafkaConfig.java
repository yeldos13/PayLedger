package dev.sozakbay.payledger.fraud;

import dev.sozakbay.payledger.events.TransferCompleted;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

@Configuration(proxyBeanMethods = false)
class FraudKafkaConfig {

    static final String DLT = TransferCompleted.TOPIC + ".DLT";

    @Bean
    NewTopic transfersDeadLetterTopic() {
        return new NewTopic(DLT, 3, (short) 1);
    }

    /** Retries a failed event twice, then parks it in the DLT so one bad message cannot block the partition. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, e) -> new TopicPartition(DLT, record.partition()));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000, 2));
        // Malformed JSON stays malformed; retrying it only delays the next events.
        handler.addNotRetryableExceptions(JacksonException.class);
        return handler;
    }
}
