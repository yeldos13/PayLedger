package dev.sozakbay.payledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sozakbay.payledger.TestcontainersConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class PersistenceTest {

    @Autowired AccountRepository accounts;
    @Autowired TransferRepository transfers;
    @Autowired EntityManager em;

    @Test
    void savesTransferWithEntriesAndBalances() {
        var system = accounts.findByTypeAndCurrency(Account.Type.SYSTEM, "KZT").orElseThrow();
        var alice = accounts.save(new Account("KZT"));

        var id = transfers.save(Transfer.execute(system, alice, 5_000)).getId();
        em.flush();
        em.clear();

        var saved = transfers.findById(id).orElseThrow();
        assertThat(saved.getEntries()).extracting(Entry::getAmount).containsExactlyInAnyOrder(-5_000L, 5_000L);
        assertThat(accounts.findById(alice.getId()).orElseThrow().getBalance()).isEqualTo(5_000);
    }
}
