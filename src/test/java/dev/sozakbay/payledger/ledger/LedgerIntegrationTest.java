package dev.sozakbay.payledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.sozakbay.payledger.TestcontainersConfig;
import dev.sozakbay.payledger.events.TransferCompleted;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.web.servlet.MockMvc;

/** End to end: HTTP -> PostgreSQL -> outbox -> Kafka, on real containers. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class LedgerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsumerFactory<String, String> consumers;

    @Test
    void paymentFlowReachesKafkaAndKeepsLedgerBalanced() throws Exception {
        var alice = send("/accounts", "{\"currency\":\"KZT\"}", 201);
        var bob = send("/accounts", "{\"currency\":\"KZT\"}", 201);
        var deposit = send("/accounts/" + alice + "/deposits", "{\"amount\":10000}", 201);
        var first = send("/transfers", transfer(alice, bob, 2500), 201);
        var second = send("/transfers", transfer(bob, alice, 500), 201);
        send("/transfers", transfer(bob, alice, 999_999), 422);

        assertThat(receivedEvents(Set.of(deposit, first, second))).containsOnlyKeys(deposit, first, second)
                .hasEntrySatisfying(first, json -> assertThat(json).containsPattern("\"amount\": ?2500"));

        // Double-entry invariants over the whole database, including rows written by other tests.
        assertThat(jdbc.queryForObject("select coalesce(sum(amount), 0) from entries", Long.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from accounts a
                where a.balance <> (select coalesce(sum(e.amount), 0) from entries e where e.account_id = a.id)
                """, Long.class)).isZero();
        assertThat(jdbc.queryForObject("select balance from accounts where id = ?::uuid", Long.class, alice))
                .isEqualTo(8000);
    }

    /** Reads the topic until every expected transfer id has arrived as a message key. */
    private Map<String, String> receivedEvents(Set<String> transferIds) {
        var props = new Properties();
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var found = new HashMap<String, String>();
        try (var consumer = consumers.createConsumer("test-" + UUID.randomUUID(), null, null, props)) {
            consumer.subscribe(List.of(TransferCompleted.TOPIC));
            for (long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                 !found.keySet().containsAll(transferIds) && System.nanoTime() < deadline; ) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (transferIds.contains(record.key())) {
                        found.put(record.key(), record.value());
                    }
                }
            }
        }
        return found;
    }

    private String send(String url, String body, int expectedStatus) throws Exception {
        var response = mvc.perform(post(url).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return expectedStatus == 201 ? JsonPath.read(response, "$.id") : null;
    }

    private static String transfer(String from, String to, long amount) {
        return "{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":%d}".formatted(from, to, amount);
    }
}
