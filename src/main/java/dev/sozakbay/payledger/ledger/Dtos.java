package dev.sozakbay.payledger.ledger;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

record CreateAccountRequest(@NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {
}

record DepositRequest(@Positive long amount) {
}

record TransferRequest(@NotNull UUID fromAccountId, @NotNull UUID toAccountId, @Positive long amount) {
}

record AccountDto(UUID id, String currency, Account.Type type, long balance, Instant createdAt) {
    static AccountDto of(Account a) {
        return new AccountDto(a.getId(), a.getCurrency(), a.getType(), a.getBalance(), a.getCreatedAt());
    }
}

record TransferDto(UUID id, UUID fromAccountId, UUID toAccountId, long amount, String currency, Instant createdAt) {
    static TransferDto of(Transfer t) {
        return new TransferDto(t.getId(), t.getFrom().getId(), t.getTo().getId(), t.getAmount(), t.getCurrency(),
                t.getCreatedAt());
    }
}

record EntryDto(UUID transferId, long amount, Instant createdAt) {
    static EntryDto of(Entry e) {
        return new EntryDto(e.getTransfer().getId(), e.getAmount(), e.getCreatedAt());
    }
}
