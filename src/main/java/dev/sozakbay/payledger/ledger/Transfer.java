package dev.sozakbay.payledger.ledger;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Money moved from one account to another, recorded as two balanced entries (double entry). */
@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Account from;
    @ManyToOne(fetch = FetchType.LAZY)
    private Account to;
    private long amount;
    private String currency;
    private Instant createdAt;
    @OneToMany(mappedBy = "transfer", cascade = CascadeType.PERSIST)
    private List<Entry> entries;

    protected Transfer() {
    }

    private Transfer(Account from, Account to, long amount) {
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.currency = from.getCurrency();
        this.createdAt = Instant.now();
        this.entries = List.of(new Entry(this, from, -amount), new Entry(this, to, amount));
    }

    /** Moves the money between the accounts and returns the transfer with its entries. */
    public static Transfer execute(Account from, Account to, long amount) {
        if (amount <= 0) {
            throw new LedgerException("Amount must be positive");
        }
        if (from == to) {
            throw new LedgerException("Cannot transfer to the same account");
        }
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new LedgerException("Accounts have different currencies");
        }
        from.debit(amount);
        to.credit(amount);
        return new Transfer(from, to, amount);
    }

    public UUID getId() { return id; }
    public Account getFrom() { return from; }
    public Account getTo() { return to; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Entry> getEntries() { return entries; }
}
