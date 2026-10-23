package dev.sozakbay.payledger.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    public enum Type {
        /** Belongs to a client, can never go below zero. */
        CUSTOMER,
        /** The bank's own funding account per currency, the other side of every deposit. Goes negative. */
        SYSTEM
    }

    @Id
    @GeneratedValue
    private UUID id;
    private String currency;
    @Enumerated(EnumType.STRING)
    private Type type;
    /** Minor units (tiyn, cents), so no floating point. */
    private long balance;
    private Instant createdAt;

    protected Account() {
    }

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
