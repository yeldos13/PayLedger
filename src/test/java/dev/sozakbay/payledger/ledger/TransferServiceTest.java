package dev.sozakbay.payledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sozakbay.payledger.TestcontainersConfig;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

@SpringBootTest
@Import(TestcontainersConfig.class)
class TransferServiceTest {

    @Autowired TransferService service;
    @Autowired AccountRepository accounts;

    @Test
    void rejectsTransferWithoutEnoughMoney() {
        var alice = funded(100);
        var bob = service.openAccount("KZT").getId();

        assertThatThrownBy(() -> service.transfer(alice, bob, 101))
                .isInstanceOf(LedgerException.class).hasMessage("Insufficient funds");
        assertThat(balance(alice)).isEqualTo(100);
    }

    @Test
    void rejectsUnsupportedCurrency() {
        assertThatThrownBy(() -> service.openAccount("XYZ")).isInstanceOf(LedgerException.class);
    }

    @Test
    void parallelTransfersCannotOverdraw() throws Exception {
        var alice = funded(100);
        var bob = service.openAccount("KZT").getId();
        var start = new CountDownLatch(1);
        var results = new ArrayList<Future<Transfer>>();
        try (var pool = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return service.transfer(alice, bob, 30);
                }));
            }
            start.countDown();
        }

        long succeeded = 0;
        for (var result : results) {
            try {
                result.get();
                succeeded++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOfAny(LedgerException.class, OptimisticLockingFailureException.class);
            }
        }
        assertThat(succeeded).isBetween(1L, 3L);
        assertThat(balance(alice)).isEqualTo(100 - 30 * succeeded);
        assertThat(balance(bob)).isEqualTo(30 * succeeded);
    }

    private UUID funded(long amount) {
        var id = service.openAccount("KZT").getId();
        service.deposit(id, amount);
        return id;
    }

    private long balance(UUID id) {
        return accounts.findById(id).orElseThrow().getBalance();
    }
}
