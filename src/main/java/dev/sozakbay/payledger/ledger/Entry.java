package dev.sozakbay.payledger.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One side of a transfer: a negative amount debits the account, a positive one credits it. */
@Entity
@Table(name = "entries")
public class Entry {

    @Id
    @GeneratedValue
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Transfer transfer;
    @ManyToOne(fetch = FetchType.LAZY)
    private Account account;
    private long amount;
    private Instant createdAt;

    protected Entry() {
    }

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
