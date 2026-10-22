package dev.sozakbay.payledger.ledger;

import java.time.Instant;

/** One side of a transfer: a negative amount debits the account, a positive one credits it. */
public class Entry {

    private Long id;
    private Transfer transfer;
    private Account account;
    private long amount;
    private Instant createdAt;

    Entry(Transfer transfer, Account account, long amount) {
        this.transfer = transfer;
        this.account = account;
        this.amount = amount;
        this.createdAt = transfer.getCreatedAt();
    }

    public Transfer getTransfer() { return transfer; }
    public Account getAccount() { return account; }
    public long getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
