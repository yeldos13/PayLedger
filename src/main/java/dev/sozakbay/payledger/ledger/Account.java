package dev.sozakbay.payledger.ledger;

import java.time.Instant;
import java.util.UUID;

public class Account {

    public enum Type {
        /** Belongs to a client, can never go below zero. */
        CUSTOMER,
        /** The bank's own funding account per currency, the other side of every deposit. Goes negative. */
        SYSTEM
    }

    private UUID id;
    private String currency;
    private Type type;
    /** Minor units (tiyn, cents), so no floating point. */
    private long balance;
    private Instant createdAt;

    public Account(String currency) {
        this(currency, Type.CUSTOMER);
    }

    Account(String currency, Type type) {
        this.currency = currency;
        this.type = type;
        this.createdAt = Instant.now();
    }

    void debit(long amount) {
        if (type == Type.CUSTOMER && balance < amount) {
            throw new LedgerException("Insufficient funds");
        }
        balance -= amount;
    }

    void credit(long amount) {
        balance += amount;
    }

    public UUID getId() { return id; }
    public String getCurrency() { return currency; }
    public Type getType() { return type; }
    public long getBalance() { return balance; }
    public Instant getCreatedAt() { return createdAt; }
}
