package dev.sozakbay.payledger.fraud;

import static dev.sozakbay.payledger.fraud.FraudChecker.LARGE_AMOUNT;
import static dev.sozakbay.payledger.fraud.FraudChecker.VELOCITY;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sozakbay.payledger.TestcontainersConfig;
import dev.sozakbay.payledger.events.TransferCompleted;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfig.class)
class FraudCheckerTest {

    @Autowired FraudChecker checker;
    @Autowired JdbcTemplate jdbc;

    @Test
    void flagsLargeAmountFromTheLimitUp() {
        var limit = FraudChecker.LARGE_AMOUNT_LIMITS.get("KZT");

        assertThat(checker.check(transfer(UUID.randomUUID(), limit - 1, Instant.now()))).isEmpty();
        var large = transfer(UUID.randomUUID(), limit, Instant.now());
        assertThat(checker.check(large)).containsExactly(LARGE_AMOUNT);
        assertThat(jdbc.queryForObject("select rule from fraud_alerts where transfer_id = ?", String.class,
                large.transferId())).isEqualTo(LARGE_AMOUNT);
    }

    @Test
    void flagsManyIncomingPaymentsWithinAMinute() {
        var account = UUID.randomUUID();
        var start = Instant.now();
        for (int i = 0; i < FraudChecker.VELOCITY_LIMIT - 1; i++) {
            assertThat(checker.check(transfer(account, 100, start.plusSeconds(i)))).isEmpty();
        }

        assertThat(checker.check(transfer(account, 100, start.plusSeconds(10)))).containsExactly(VELOCITY);
        // The first payments have left the one-minute window.
        assertThat(checker.check(transfer(account, 100, start.plusSeconds(62)))).isEmpty();
    }

    private static TransferCompleted transfer(UUID toAccount, long amount, Instant at) {
        return new TransferCompleted(UUID.randomUUID(), UUID.randomUUID(), toAccount, amount, "KZT", at);
    }
}
