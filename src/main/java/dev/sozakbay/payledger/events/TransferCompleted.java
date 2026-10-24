package dev.sozakbay.payledger.events;

import java.time.Instant;
import java.util.UUID;

/** Published after a transfer commits. Field names are the JSON contract for consumers: rename only with a new version. */
public record TransferCompleted(UUID transferId, UUID fromAccountId, UUID toAccountId, long amount, String currency,
                                Instant occurredAt) {

    public static final String TOPIC = "ledger.transfers";
}
