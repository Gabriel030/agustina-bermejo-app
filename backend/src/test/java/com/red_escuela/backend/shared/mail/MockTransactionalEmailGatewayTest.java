package com.red_escuela.backend.shared.mail;

import com.red_escuela.backend.shared.domain.EmailAddress;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class MockTransactionalEmailGatewayTest {
    private final MockTransactionalEmailGateway gateway = new MockTransactionalEmailGateway();

    private TransactionalEmail email(String recipient) {
        return new TransactionalEmail(EmailAddress.of(recipient), "Asunto", "Texto", null);
    }

    @Test
    @Tag("c01")
    @Tag("D-343")
    void theSimulatedGatewayAnswersAMockMessageIdMarkedAsSimulated() {
        EmailDeliveryReceipt receipt = gateway.send(email("maria.lopez@example.com"));

        assertThat(receipt.providerMessageId()).startsWith("mock-").hasSizeGreaterThan("mock-".length());
        assertThat(receipt.simulated()).isTrue();
    }

    @Test
    @Tag("c01")
    @Tag("D-343")
    void everySimulatedMessageGetsItsOwnId() {
        assertThat(gateway.send(email("a@example.com")).providerMessageId()).isNotEqualTo(gateway.send(email("a@example.com")).providerMessageId());
    }

    @Test
    @Tag("c01")
    @Tag("D-343")
    void theInfoLogShowsTheMaskedRecipientAndNeverTheFullAddress(CapturedOutput output) {
        gateway.send(email("maria.lopez@example.com"));

        assertThat(output.getAll()).contains("ma***@example.com").doesNotContain("maria.lopez");
    }

    @Test
    void aTransactionalEmailNeedsSubjectAndPlainText() {
        EmailAddress recipient = EmailAddress.of("a@example.com");

        assertThatThrownBy(() -> new TransactionalEmail(recipient, " ", "Texto", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransactionalEmail(recipient, null, "Texto", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransactionalEmail(recipient, "Asunto", " ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransactionalEmail(null, "Asunto", "Texto", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aReceiptNeedsAMessageId() {
        assertThatThrownBy(() -> new EmailDeliveryReceipt(" ", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailDeliveryReceipt(null, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
