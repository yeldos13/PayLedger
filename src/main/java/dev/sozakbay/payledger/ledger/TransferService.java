package dev.sozakbay.payledger.ledger;

import dev.sozakbay.payledger.events.TransferCompleted;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final OutboxRepository outbox;
    private final TransactionTemplate tx;
    private final JsonMapper json;

    public TransferService(AccountRepository accounts, TransferRepository transfers, OutboxRepository outbox,
                           TransactionTemplate tx, JsonMapper json) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.outbox = outbox;
        this.tx = tx;
        this.json = json;
    }

    /** Only currencies with a system funding account can be deposited, so only those can be opened. */
    public Account openAccount(String currency) {
        if (accounts.findByTypeAndCurrency(Account.Type.SYSTEM, currency).isEmpty()) {
            throw new LedgerException("Unsupported currency " + currency);
        }
        return accounts.save(new Account(currency));
    }

    public Transfer deposit(String idempotencyKey, UUID toId, long amount) {
        var currency = account(toId).getCurrency();
        var system = accounts.findByTypeAndCurrency(Account.Type.SYSTEM, currency).orElseThrow();
        return move(idempotencyKey, system.getId(), toId, amount);
    }

    /** Customer to customer only: system accounts move money through deposits, never through this API. */
    public Transfer transfer(String idempotencyKey, UUID fromId, UUID toId, long amount) {
        if (account(fromId).getType() != Account.Type.CUSTOMER || account(toId).getType() != Account.Type.CUSTOMER) {
            throw new LedgerException("Transfers are allowed only between customer accounts");
        }
        return move(idempotencyKey, fromId, toId, amount);
    }

    private Transfer move(String idempotencyKey, UUID fromId, UUID toId, long amount) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> transferOnce(idempotencyKey, fromId, toId, amount));
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
                // A parallel transfer changed one of the accounts first, or committed the same Idempotency-Key.
                // The retry re-reads fresh balances, or finds that transfer and replays it.
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                log.atInfo().addKeyValue("attempt", attempt).log("Concurrent update, retrying transfer");
            }
        }
    }

    private Transfer transferOnce(String idempotencyKey, UUID fromId, UUID toId, long amount) {
        var existing = transfers.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().sameRequest(fromId, toId, amount)) {
                throw new LedgerException("Idempotency-Key was already used with a different request");
            }
            log.atInfo().addKeyValue("transferId", existing.get().getId()).log("Idempotent replay");
            return existing.get();
        }
        var transfer = transfers.save(Transfer.execute(idempotencyKey, account(fromId), account(toId), amount));
        // Same transaction as the transfer: the event exists if and only if the money moved.
        var event = new TransferCompleted(transfer.getId(), fromId, toId, amount, transfer.getCurrency(),
                transfer.getCreatedAt());
        outbox.save(new OutboxEvent(transfer.getId(), TransferCompleted.class.getSimpleName(),
                json.writeValueAsString(event)));
        // Logged after commit and only for new transfers, so a replay or a rollback never claims money moved.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.atInfo().addKeyValue("transferId", transfer.getId()).addKeyValue("fromAccountId", fromId)
                        .addKeyValue("toAccountId", toId).addKeyValue("amount", amount)
                        .addKeyValue("currency", transfer.getCurrency()).log("Transfer completed");
            }
        });
        return transfer;
    }

    private Account account(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
