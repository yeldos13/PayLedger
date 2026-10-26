package dev.sozakbay.payledger.fraud;

import dev.sozakbay.payledger.events.TransferCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Fraud module entry point. Talks to the ledger only through Kafka events, never through its classes. */
@Component
class TransferListener {

    private static final Logger log = LoggerFactory.getLogger(TransferListener.class);

    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    private final FraudChecker checker;

    TransferListener(JsonMapper json, JdbcTemplate jdbc, FraudChecker checker) {
        this.json = json;
        this.jdbc = jdbc;
        this.checker = checker;
    }

    @KafkaListener(topics = TransferCompleted.TOPIC, groupId = "fraud")
    @Transactional
    void onTransferCompleted(String payload, @Header("eventId") String eventId) {
        // The outbox delivers at least once; the insert commits together with the processing below.
        var firstTime = jdbc.update("""
                insert into fraud_processed_events (event_id, processed_at) values (?::uuid, now())
                on conflict do nothing""", eventId) == 1;
        if (!firstTime) {
            log.atInfo().addKeyValue("eventId", eventId).log("Duplicate event skipped");
            return;
        }
        var event = json.readValue(payload, TransferCompleted.class);
        log.atInfo().addKeyValue("eventId", eventId).addKeyValue("transferId", event.transferId())
                .addKeyValue("amount", event.amount()).addKeyValue("currency", event.currency())
                .log("Fraud check received transfer");
        checker.check(event);
    }
}
