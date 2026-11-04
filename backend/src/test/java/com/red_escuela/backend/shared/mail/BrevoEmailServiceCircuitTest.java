package com.red_escuela.backend.shared.mail;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.shared.exception.CircuitOpenException;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    private Person person;

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
        InstitutionRepository institutions = mock(InstitutionRepository.class);
        when(institutions.findAll()).thenReturn(List.of());
        person = mock(Person.class);
        when(person.emailValue()).thenReturn("student@example.com");
        when(person.getName()).thenReturn("Ana");
        CircuitBreakerFactory factory = new CircuitBreakerFactory(environment, clock, new SimpleMeterRegistry());
        service = new BrevoEmailService(new CircuitBreakingMailSender(mailSender, factory.breakerNamed(MailCircuit.NAME)),
                institutions, "from@example.com");
    }

    @Test
    void anOpenCircuitRaisesCircuitOpenWithoutTouchingSmtpAndSendsAgainOnceItCloses() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.sendPasswordResetCode(person, "123456")).isInstanceOf(EmailDeliveryException.class);
        }

        assertThatThrownBy(() -> service.sendPasswordResetCode(person, "123456")).isInstanceOf(CircuitOpenException.class);
        assertThatThrownBy(() -> service.sendEmailVerification(person, "https://x")).isInstanceOf(CircuitOpenException.class);
        assertThatThrownBy(() -> service.sendEmailChangeNotice("Ana", "a@example.com", "b@example.com")).isInstanceOf(CircuitOpenException.class);
        verify(mailSender, times(2)).send(any(MimeMessage.class));

        doNothing().when(mailSender).send(any(MimeMessage.class));
        now = now.plus(31, ChronoUnit.SECONDS);
        service.sendPasswordResetCode(person, "123456");

        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void invalidRecipientFailuresDoNotOpenTheCircuit() {
        doThrow(new MailParseException("bad address")).when(mailSender).send(any(MimeMessage.class));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.sendPasswordResetCode(person, "123456")).isInstanceOf(EmailDeliveryException.class);
        }

        verify(mailSender, times(5)).send(any(MimeMessage.class));
        assertThat(MailCircuit.countsAsProviderFailure(new MailSendException("smtp down"))).isTrue();
        assertThat(MailCircuit.countsAsProviderFailure(new MailParseException("bad"))).isFalse();
    }
}
