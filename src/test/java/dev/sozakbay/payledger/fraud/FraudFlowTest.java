package dev.sozakbay.payledger.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sozakbay.payledger.TestcontainersConfig;
import dev.sozakbay.payledger.ledger.TransferService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Ledger -> outbox -> Kafka -> fraud rules, with nothing mocked. */
@SpringBootTest
@Import(TestcontainersConfig.class)
class FraudFlowTest {

    @Autowired TransferService ledger;
    @Autowired JdbcTemplate jdbc;

    @Test
    void burstOfPaymentsToOneAccountRaisesVelocityAlert() throws InterruptedException {
        var mule = ledger.openAccount("KZT").getId();
        var transferIds = new ArrayList<UUID>();
        for (int i = 0; i < FraudChecker.VELOCITY_LIMIT; i++) {
            transferIds.add(ledger.deposit(UUID.randomUUID().toString(), mule, 1_000).getId());
        }

        var alertsSql = "select count(*) from fraud_alerts where rule = 'VELOCITY' and transfer_id = any(?)";
        var ids = transferIds.toArray(UUID[]::new);
        long alerts = 0;
        for (long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
             alerts == 0 && System.nanoTime() < deadline; ) {
            Thread.sleep(200);
            alerts = jdbc.queryForObject(alertsSql, Long.class, (Object) ids);
        }

        assertThat(alerts).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from fraud_alerts where rule = 'LARGE_AMOUNT' and transfer_id = any(?)",
                Long.class, (Object) ids)).isZero();
    }
}
