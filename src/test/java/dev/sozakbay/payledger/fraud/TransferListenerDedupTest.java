package dev.sozakbay.payledger.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sozakbay.payledger.TestcontainersConfig;
import dev.sozakbay.payledger.events.TransferCompleted;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class TransferListenerDedupTest {

    @Autowired TransferListener listener;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    @Test
    void redeliveredEventIsProcessedOnce(CapturedOutput output) {
        var eventId = UUID.randomUUID().toString();
        var payload = json.writeValueAsString(new TransferCompleted(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 10, "KZT", Instant.now()));

        listener.onTransferCompleted(payload, eventId);
        listener.onTransferCompleted(payload, eventId);

        assertThat(jdbc.queryForObject("select count(*) from fraud_processed_events where event_id = ?::uuid",
                Long.class, eventId)).isEqualTo(1);
        assertThat(output).containsPattern("\"message\":\"Fraud check received transfer\".*\"eventId\":\"" + eventId)
                .containsPattern("\"message\":\"Duplicate event skipped\".*\"eventId\":\"" + eventId);
    }
}
