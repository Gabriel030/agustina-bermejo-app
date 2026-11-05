package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.events.PasswordResetCodeIssuedEvent;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import com.red_escuela.backend.shared.mail.EmailService;
import com.red_escuela.backend.shared.mail.MailRecipient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdentityEmailDispatcherRecipientTest {

    private final EmailService emailService = mock(EmailService.class);
    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final InstitutionNameResolver nameResolver = mock(InstitutionNameResolver.class);
    private IdentityEmailDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        when(effectGuard.runEffectOnce(any(), anyString(), any())).thenAnswer(call -> {
            call.<Runnable>getArgument(2).run();
            return true;
        });
        when(nameResolver.resolveInstitutionName()).thenReturn("Instituto Norte");
        dispatcher = new IdentityEmailDispatcher(emailService, personRepository, effectGuard, nameResolver);
    }

    @Test
    void usesPersonNameWhenPresent() {
        assertThat(recipientFor("Ana", "ana.user", "ana@example.com").name()).isEqualTo("Ana");
    }

    @Test
    void fallsBackToUsernameWhenNameIsNull() {
        assertThat(recipientFor(null, "ana.user", "ana@example.com").name()).isEqualTo("ana.user");
    }

    @Test
    void fallsBackToEmailLocalPartWhenNameAndUsernameAreMissing() {
        assertThat(recipientFor("  ", null, "ana.lopez@example.com").name()).isEqualTo("ana.lopez");
    }

    @Test
    void skipsSendingWhenEmailIsMissing() {
        for (String email : new String[] {null, " "}) {
            Person person = mock(Person.class);
            when(person.emailValue()).thenReturn(email);
            when(personRepository.findById(8L)).thenReturn(Optional.of(person));

            dispatcher.onPasswordResetCodeIssued(
                    new PasswordResetCodeIssuedEvent(UUID.randomUUID(), "corr", Instant.now(), 8L, "123456"));
            dispatcher.onEmailVerificationIssued(
                    new EmailVerificationIssuedEvent(UUID.randomUUID(), "corr", Instant.now(), 8L, "https://example.test/verify"));
        }

        verifyNoInteractions(emailService);
    }

    private MailRecipient recipientFor(String name, String username, String email) {
        Person person = mock(Person.class);
        when(person.getId()).thenReturn(7L);
        when(person.getName()).thenReturn(name);
        when(person.getUsername()).thenReturn(username);
        when(person.emailValue()).thenReturn(email);
        when(personRepository.findById(7L)).thenReturn(Optional.of(person));

        dispatcher.onPasswordResetCodeIssued(
                new PasswordResetCodeIssuedEvent(UUID.randomUUID(), "corr", Instant.now(), 7L, "123456"));

        ArgumentCaptor<MailRecipient> captor = ArgumentCaptor.forClass(MailRecipient.class);
        verify(emailService).sendPasswordResetCode(captor.capture(), org.mockito.ArgumentMatchers.eq("Instituto Norte"), org.mockito.ArgumentMatchers.eq("123456"));
        return captor.getValue();
    }
}
