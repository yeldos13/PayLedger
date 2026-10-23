package dev.sozakbay.payledger.ledger;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public Transfer deposit(UUID toId, long amount) {
        var currency = account(toId).getCurrency();
        var system = accounts.findByTypeAndCurrency(Account.Type.SYSTEM, currency).orElseThrow();
        return transfer(system.getId(), toId, amount);
    }

    public Transfer transfer(UUID fromId, UUID toId, long amount) {
        for (int attempt = 1; ; attempt++) {
            try {
                var transfer = tx.execute(status ->
                        transfers.save(Transfer.execute(account(fromId), account(toId), amount)));
                log.atInfo().addKeyValue("transferId", transfer.getId()).addKeyValue("fromAccountId", fromId)
                        .addKeyValue("toAccountId", toId).addKeyValue("amount", amount)
                        .addKeyValue("currency", transfer.getCurrency()).log("Transfer completed");
                return transfer;
            } catch (OptimisticLockingFailureException e) {
                // A parallel transfer changed one of the accounts first; retry re-reads the fresh balances.
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                log.atInfo().addKeyValue("attempt", attempt).log("Concurrent update, retrying transfer");
            }
        }
    }

    private Account account(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
