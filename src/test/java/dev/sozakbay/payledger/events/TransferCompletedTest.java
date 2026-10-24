package dev.sozakbay.payledger.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TransferCompletedTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void roundTripsThroughJsonWithStableFieldNames() {
        var event = new TransferCompleted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_500, "KZT",
                Instant.parse("2026-01-01T10:00:00Z"));

        var json = mapper.writeValueAsString(event);

        assertThat(json).contains("\"transferId\"", "\"fromAccountId\"", "\"toAccountId\"", "\"amount\":1500",
                "\"currency\":\"KZT\"", "\"occurredAt\":\"2026-01-01T10:00:00Z\"");
        assertThat(mapper.readValue(json, TransferCompleted.class)).isEqualTo(event);
    }
}
