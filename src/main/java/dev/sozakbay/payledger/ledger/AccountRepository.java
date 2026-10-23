package dev.sozakbay.payledger.ledger;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByTypeAndCurrency(Account.Type type, String currency);
}
