package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.service.EmailVerificationIssuedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationSenderTest {

    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final EmailVerificationTokenRepository tokenRepository = mock(EmailVerificationTokenRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final EmailVerificationSender sender =
            new EmailVerificationSender(studentRepository, tokenRepository, eventPublisher, "https://app.test");

    @Test
    void publishesVerificationEventWithTokenForTheStudentPerson() {
        Person person = new Person();
        person.setId(7L);
        Student student = new Student();
        student.setPerson(person);
        when(studentRepository.findById(3L)).thenReturn(Optional.of(student));

        sender.sendVerificationToStudent(3L);

        verify(tokenRepository).deleteByPerson(person);
        ArgumentCaptor<EmailVerificationToken> token = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(token.capture());
        assertThat(token.getValue().getPerson()).isSameAs(person);
        assertThat(token.getValue().getExpiresAt()).isNotNull();
        ArgumentCaptor<EmailVerificationIssuedEvent> event = ArgumentCaptor.forClass(EmailVerificationIssuedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().personId()).isEqualTo(7L);
        assertThat(event.getValue().verificationLink()).startsWith("https://app.test/verificar-email?token=");
    }

    @Test
    void throwsStudentNotFoundWhenStudentDoesNotExist() {
        when(studentRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sender.sendVerificationToStudent(3L)).isInstanceOf(StudentNotFoundException.class);

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
