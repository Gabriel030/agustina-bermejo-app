package com.red_escuela.backend.shared.mail;

import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrevoEmailServiceOwnerApprovalTest {

    @Test
    void ownerApprovalMailEscapesTheDataAndCarriesBothLinksWithTheSameToken() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("resilience.default.enabled", "true")
                .withProperty("resilience.default.window", "2")
                .withProperty("resilience.default.failure-threshold", "2")
                .withProperty("resilience.default.wait-open", "30s");
        CircuitBreakerFactory factory = new CircuitBreakerFactory(environment, Clock.systemUTC(), new SimpleMeterRegistry());
        BrevoEmailService service = new BrevoEmailService(new CircuitBreakingMailSender(mailSender, factory.breakerNamed(MailCircuit.NAME)),
                "from@example.com");

        service.sendEmailChangeOwnerApproval("<b>Ana</b>", "actual@example.com", "nuevo<x>@example.com",
                "https://example.test/revisar-cambio-email?token=abc&x=1", "https://example.test/cancelar-cambio-email?token=abc&x=1");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        String html = message.getContent().toString();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("actual@example.com");
        assertThat(html).contains("&lt;b&gt;Ana&lt;/b&gt;").contains("nuevo&lt;x&gt;@example.com").doesNotContain("<b>Ana</b>");
        assertThat(html).contains("href=\"https://example.test/revisar-cambio-email?token=abc&amp;x=1\"")
                .contains("href=\"https://example.test/cancelar-cambio-email?token=abc&amp;x=1\"");
    }
}
