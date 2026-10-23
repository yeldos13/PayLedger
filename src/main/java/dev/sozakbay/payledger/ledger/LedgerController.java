package dev.sozakbay.payledger.ledger;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class LedgerController {

    private final TransferService service;
    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final EntryRepository entries;

    public LedgerController(TransferService service, AccountRepository accounts, TransferRepository transfers,
                            EntryRepository entries) {
        this.service = service;
        this.accounts = accounts;
        this.transfers = transfers;
        this.entries = entries;
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDto openAccount(@Valid @RequestBody CreateAccountRequest request) {
        return AccountDto.of(service.openAccount(request.currency()));
    }

    @GetMapping("/accounts/{id}")
    public AccountDto getAccount(@PathVariable UUID id) {
        return AccountDto.of(accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id)));
    }

    @GetMapping("/accounts/{id}/entries")
    public List<EntryDto> getEntries(@PathVariable UUID id) {
        if (!accounts.existsById(id)) {
            throw new AccountNotFoundException(id);
        }
        return entries.findByAccount_IdOrderByCreatedAtDesc(id).stream().map(EntryDto::of).toList();
    }

    @PostMapping("/accounts/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferDto deposit(@PathVariable UUID id, @Valid @RequestBody DepositRequest request) {
        return TransferDto.of(service.deposit(id, request.amount()));
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferDto transfer(@Valid @RequestBody TransferRequest request) {
        return TransferDto.of(service.transfer(request.fromAccountId(), request.toAccountId(), request.amount()));
    }

    @GetMapping("/transfers/{id}")
    public TransferDto getTransfer(@PathVariable UUID id) {
        return TransferDto.of(transfers.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer " + id + " not found")));
    }
}
