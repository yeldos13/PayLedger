package dev.sozakbay.payledger.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    List<Entry> findByAccount_IdOrderByCreatedAtDesc(UUID accountId);
}
