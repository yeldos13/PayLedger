package dev.sozakbay.payledger.fraud;

import dev.sozakbay.payledger.events.TransferCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Fraud module entry point. Talks to the ledger only through Kafka events, never through its classes. */
@Component
class TransferListener {

    private static final Logger log = LoggerFactory.getLogger(TransferListener.class);

    private final JsonMapper json;

    TransferListener(JsonMapper json) {
        this.json = json;
    }

    @KafkaListener(topics = TransferCompleted.TOPIC, groupId = "fraud")
    void onTransferCompleted(String payload) {
        var event = json.readValue(payload, TransferCompleted.class);
        log.atInfo().addKeyValue("transferId", event.transferId()).addKeyValue("amount", event.amount())
                .addKeyValue("currency", event.currency()).log("Fraud check received transfer");
    }
}
