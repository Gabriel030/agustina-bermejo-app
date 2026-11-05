package com.red_escuela.backend.shared.mail;

import com.red_escuela.backend.shared.exception.CircuitOpenException;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrevoEmailServiceCircuitTest {

    private Instant now;
    private JavaMailSender mailSender;
    private BrevoEmailService service;
    private MailRecipient recipient;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-11-04T12:00:00Z");
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now;
            }
        };
        MockEnvironment environment = new MockEnvironment()
                .withProperty("resilience.default.enabled", "true")
                .withProperty("resilience.default.window", "2")
                .withProperty("resilience.default.failure-threshold", "2")
                .withProperty("resilience.default.wait-open", "30s");
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
        recipient = new MailRecipient(7L, "Ana", "student@example.com");
        CircuitBreakerFactory factory = new CircuitBreakerFactory(environment, clock, new SimpleMeterRegistry());
        service = new BrevoEmailService(new CircuitBreakingMailSender(mailSender, factory.breakerNamed(MailCircuit.NAME)),
                "from@example.com");
    }

    @Test
    void anOpenCircuitRaisesCircuitOpenWithoutTouchingSmtpAndSendsAgainOnceItCloses() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.sendPasswordResetCode(recipient, "Red Escuela", "123456")).isInstanceOf(EmailDeliveryException.class);
        }

        assertThatThrownBy(() -> service.sendPasswordResetCode(recipient, "Red Escuela", "123456")).isInstanceOf(CircuitOpenException.class);
        assertThatThrownBy(() -> service.sendEmailVerification(recipient, "Red Escuela", "https://x")).isInstanceOf(CircuitOpenException.class);
        assertThatThrownBy(() -> service.sendEmailChangeNotice("Ana", "a@example.com", "b@example.com")).isInstanceOf(CircuitOpenException.class);
        verify(mailSender, times(2)).send(any(MimeMessage.class));

        doNothing().when(mailSender).send(any(MimeMessage.class));
        now = now.plus(31, ChronoUnit.SECONDS);
        service.sendPasswordResetCode(recipient, "Red Escuela", "123456");

        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void invalidRecipientFailuresDoNotOpenTheCircuit() {
        doThrow(new MailParseException("bad address")).when(mailSender).send(any(MimeMessage.class));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.sendPasswordResetCode(recipient, "Red Escuela", "123456")).isInstanceOf(EmailDeliveryException.class);
        }

        verify(mailSender, times(5)).send(any(MimeMessage.class));
        assertThat(MailCircuit.countsAsProviderFailure(new MailSendException("smtp down"))).isTrue();
        assertThat(MailCircuit.countsAsProviderFailure(new MailParseException("bad"))).isFalse();
    }

    @Test
    void passwordResetMailContainsInstitutionNameRecipientNameAndCode() throws Exception {
        service.sendPasswordResetCode(recipient, "Instituto Norte", "654321");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        String html = mailContent(captor.getValue());
        assertThat(html).contains("Instituto Norte").contains("Ana").contains("654321");
        assertThat(captor.getValue().getAllRecipients()[0].toString()).isEqualTo("student@example.com");
    }

    @Test
    void verificationMailContainsInstitutionNameRecipientNameAndLink() throws Exception {
        service.sendEmailVerification(recipient, "Instituto Norte", "https://example.test/verify");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        String html = mailContent(captor.getValue());
        assertThat(html).contains("Instituto Norte").contains("Ana").contains("https://example.test/verify");
    }

    private String mailContent(MimeMessage message) throws Exception {
        message.saveChanges();
        return String.valueOf(message.getContent() instanceof jakarta.mail.Multipart multipart
                ? multipart.getBodyPart(0).getContent()
                : message.getContent());
    }
}
