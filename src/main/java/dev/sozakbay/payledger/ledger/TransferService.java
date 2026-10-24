package dev.sozakbay.payledger.ledger;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final TransactionTemplate tx;

    public TransferService(AccountRepository accounts, TransferRepository transfers, TransactionTemplate tx) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.tx = tx;
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
        return transfer(idempotencyKey, system.getId(), toId, amount);
    }

    public Transfer transfer(String idempotencyKey, UUID fromId, UUID toId, long amount) {
        for (int attempt = 1; ; attempt++) {
            try {
                var transfer = tx.execute(status -> transferOnce(idempotencyKey, fromId, toId, amount));
                log.atInfo().addKeyValue("transferId", transfer.getId()).addKeyValue("fromAccountId", fromId)
                        .addKeyValue("toAccountId", toId).addKeyValue("amount", amount)
                        .addKeyValue("currency", transfer.getCurrency()).log("Transfer completed");
                return transfer;
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
        return transfers.save(Transfer.execute(idempotencyKey, account(fromId), account(toId), amount));
    }

    private Account account(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
