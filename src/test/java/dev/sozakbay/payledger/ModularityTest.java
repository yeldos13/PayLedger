package dev.sozakbay.payledger;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** ledger and fraud may share only the events contract; neither may reach into the other. */
class ModularityTest {

    @Test
    void modulesRespectBoundaries() {
        ApplicationModules.of(PayLedgerApplication.class).verify();
    }
}
