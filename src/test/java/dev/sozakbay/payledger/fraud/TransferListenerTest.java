package dev.sozakbay.payledger.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sozakbay.payledger.TestcontainersConfig;
import dev.sozakbay.payledger.ledger.TransferService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class TransferListenerTest {

    @Autowired TransferService ledger;

    @Test
    void receivesCompletedTransfersFromKafka(CapturedOutput output) throws InterruptedException {
        var account = ledger.openAccount("KZT").getId();
        var transferId = ledger.deposit(UUID.randomUUID().toString(), account, 42).getId();

        var expected = "\"message\":\"Fraud check received transfer\".*\"transferId\":\"" + transferId;
        for (long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
             !output.getOut().matches("(?s).*" + expected + ".*") && System.nanoTime() < deadline; ) {
            Thread.sleep(200);
        }
        assertThat(output).containsPattern(expected);
    }
}
