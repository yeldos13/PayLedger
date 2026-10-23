package dev.sozakbay.payledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class PayLedgerApplicationTests {

    @Test
    void contextLoads() {
    }
}
