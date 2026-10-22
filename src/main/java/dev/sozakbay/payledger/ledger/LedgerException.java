package dev.sozakbay.payledger.ledger;

/** A business rule of the ledger was violated, e.g. insufficient funds. */
public class LedgerException extends RuntimeException {

    public LedgerException(String message) {
        super(message);
    }
}
