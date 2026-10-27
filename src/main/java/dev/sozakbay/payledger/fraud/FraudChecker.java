package dev.sozakbay.payledger.fraud;

import dev.sozakbay.payledger.events.TransferCompleted;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Simple rules over completed transfers. An alert only flags a transfer for review; it never blocks it. */
@Component
class FraudChecker {

    static final String LARGE_AMOUNT = "LARGE_AMOUNT";
    static final String VELOCITY = "VELOCITY";

    /** Minor units: 500 000 KZT, 1 000 USD/EUR. */
    static final Map<String, Long> LARGE_AMOUNT_LIMITS = Map.of("KZT", 50_000_000L, "USD", 100_000L, "EUR", 100_000L);
    /** Many incoming payments to one account in a short window is a typical money-mule pattern. */
    static final int VELOCITY_LIMIT = 5;
    static final Duration VELOCITY_WINDOW = Duration.ofMinutes(1);

    private static final Logger log = LoggerFactory.getLogger(FraudChecker.class);

    private final JdbcTemplate jdbc;

    FraudChecker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<String> check(TransferCompleted event) {
        var at = Timestamp.from(event.occurredAt());
        jdbc.update("insert into fraud_transfers (transfer_id, to_account_id, occurred_at) values (?, ?, ?)",
                event.transferId(), event.toAccountId(), at);

        var alerts = new ArrayList<String>();
        if (event.amount() >= LARGE_AMOUNT_LIMITS.getOrDefault(event.currency(), Long.MAX_VALUE)) {
            alerts.add(LARGE_AMOUNT);
        }
        // No upper bound: events from different partitions arrive out of order, later payments still count.
        var recent = jdbc.queryForObject("""
                select count(*) from fraud_transfers
                where to_account_id = ? and occurred_at > ?""",
                Long.class, event.toAccountId(), Timestamp.from(event.occurredAt().minus(VELOCITY_WINDOW)));
        if (recent >= VELOCITY_LIMIT) {
            alerts.add(VELOCITY);
        }

        for (var rule : alerts) {
            jdbc.update("insert into fraud_alerts (transfer_id, rule, created_at) values (?, ?, now())",
                    event.transferId(), rule);
            log.atWarn().addKeyValue("transferId", event.transferId()).addKeyValue("rule", rule)
                    .log("Fraud alert raised");
        }
        return alerts;
    }
}
