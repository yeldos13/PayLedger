package dev.sozakbay.payledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/** Ledger rules without Spring or a database. */
class TransferTest {

    @Test
    void movesMoneyWithBalancedEntries() {
        var alice = funded(100);
        var bob = new Account("KZT");

        var transfer = Transfer.execute("key", alice, bob, 30);

        assertThat(alice.getBalance()).isEqualTo(70);
        assertThat(bob.getBalance()).isEqualTo(30);
        assertThat(transfer.getCurrency()).isEqualTo("KZT");
        assertThat(transfer.getEntries()).extracting(Entry::getAmount).containsExactly(-30L, 30L);
        assertThat(transfer.getEntries().stream().mapToLong(Entry::getAmount).sum()).isZero();
    }

    @Test
    void customerCanSpendWholeBalanceButNotMore() {
        var alice = funded(100);
        var bob = new Account("KZT");

        assertThatThrownBy(() -> Transfer.execute("key", alice, bob, 101)).hasMessage("Insufficient funds");
        Transfer.execute("key", alice, bob, 100);

        assertThat(alice.getBalance()).isZero();
    }

    @Test
    void systemAccountMayGoNegative() {
        var system = new Account("KZT", Account.Type.SYSTEM);

        Transfer.execute("key", system, new Account("KZT"), 500);

        assertThat(system.getBalance()).isEqualTo(-500);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveAmount(long amount) {
        var alice = funded(100);

        assertThatThrownBy(() -> Transfer.execute("key", alice, new Account("KZT"), amount))
                .isInstanceOf(LedgerException.class).hasMessage("Amount must be positive");
        assertThat(alice.getBalance()).isEqualTo(100);
    }

    @Test
    void rejectsSameAccount() {
        var alice = funded(100);

        assertThatThrownBy(() -> Transfer.execute("key", alice, alice, 10))
                .hasMessage("Cannot transfer to the same account");
    }

    @Test
    void rejectsDifferentCurrenciesWithoutMovingMoney() {
        var alice = funded(100);
        var bob = new Account("USD");

        assertThatThrownBy(() -> Transfer.execute("key", alice, bob, 10))
                .hasMessage("Accounts have different currencies");
        assertThat(alice.getBalance()).isEqualTo(100);
        assertThat(bob.getBalance()).isZero();
    }

    @Test
    void rejectsOverflowInsteadOfWrappingAround() {
        var system = new Account("KZT", Account.Type.SYSTEM);
        Transfer.execute("key", system, new Account("KZT"), 10);

        assertThatThrownBy(() -> Transfer.execute("key", system, new Account("KZT"), Long.MAX_VALUE))
                .isInstanceOf(LedgerException.class).hasMessage("Amount too large");
        assertThat(system.getBalance()).isEqualTo(-10);

        var rich = funded(Long.MAX_VALUE - 5);
        assertThatThrownBy(() -> Transfer.execute("key", new Account("KZT", Account.Type.SYSTEM), rich, 10))
                .hasMessage("Amount too large");
        assertThat(rich.getBalance()).isEqualTo(Long.MAX_VALUE - 5);
    }

    @Test
    void sameRequestComparesAccountsAndAmount() {
        var alice = withId(funded(100));
        var bob = withId(new Account("KZT"));
        var transfer = Transfer.execute("key", alice, bob, 30);

        assertThat(transfer.sameRequest(alice.getId(), bob.getId(), 30)).isTrue();
        assertThat(transfer.sameRequest(alice.getId(), bob.getId(), 31)).isFalse();
        assertThat(transfer.sameRequest(bob.getId(), alice.getId(), 30)).isFalse();
    }

    private static Account funded(long amount) {
        var account = new Account("KZT");
        account.credit(amount);
        return account;
    }

    private static Account withId(Account account) {
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        return account;
    }
}
