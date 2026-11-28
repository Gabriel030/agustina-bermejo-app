package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.dto.UpdateStudentRequest;
import com.red_escuela.backend.identity.entity.ContactChangeType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentDocumentAlreadyExistsException;
import com.red_escuela.backend.identity.exception.StudentEmailChangeGoogleAccountException;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.specialist.ContactChangeRecorder;
import com.red_escuela.backend.identity.specialist.StudentEmailChanger;
import com.red_escuela.backend.shared.domain.DocumentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UpdateStudentTransactionTest {
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final ContactChangeRecorder contactChangeRecorder = mock(ContactChangeRecorder.class);
    private final StudentEmailChanger studentEmailChanger = mock(StudentEmailChanger.class);
    private final UpdateStudentTransaction transaction =
            new UpdateStudentTransaction(studentRepository, personRepository, contactChangeRecorder, studentEmailChanger);

    private Student student;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("77", null));
        Person person = new Person();
        person.setId(10L);
        person.setName("Ana");
        person.setLastname("Pérez");
        person.setEmail("ana@example.com");
        person.changeDocument(DocumentType.DNI, "30111222");
        student = new Student();
        student.setId(5L);
        student.setPerson(person);
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private UpdateStudentRequest request(String documentNumber, String email) {
        return new UpdateStudentRequest("Lucía", "Gómez", null, documentNumber, LocalDate.of(2001, 4, 15), "F",
                "Argentina", "1155550000", email);
    }

    @Test
    void changingDocumentRecordsTheActorAndBothValues() {
        when(personRepository.findByDocumentTypeAndDni(DocumentType.DNI, "30999888")).thenReturn(Optional.empty());

        transaction.update(5L, request("30999888", null), true);

        verify(contactChangeRecorder).recordChange(10L, 77L, ContactChangeType.DOCUMENT_CHANGED,
                "DNI:30111222", "DNI:30999888");
        assertThat(student.getPerson().getDni()).isEqualTo("30999888");
        assertThat(student.getPerson().getName()).isEqualTo("Lucía");
    }

    @Test
    void keepingTheSameDocumentSkipsAuditAndDuplicateLookup() {
        transaction.update(5L, request("30111222", null), true);

        verify(contactChangeRecorder, never()).recordChange(any(), any(), any(), any(), any());
        verify(personRepository, never()).findByDocumentTypeAndDni(any(), any());
    }

    @Test
    void documentOwnedByAnotherPersonIsRejectedBeforeAnyChange() {
        Person owner = new Person();
        owner.setId(99L);
        when(personRepository.findByDocumentTypeAndDni(DocumentType.DNI, "30555666")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> transaction.update(5L, request("30555666", null), true))
                .isInstanceOf(StudentDocumentAlreadyExistsException.class);

        assertThat(student.getPerson().getDni()).isEqualTo("30111222");
        verify(contactChangeRecorder, never()).recordChange(any(), any(), any(), any(), any());
    }

    @Test
    void requestedEmailIsDelegatedToTheEmailChangerWithTheActor() {
        transaction.update(5L, request("30111222", "otro@example.com"), true);

        verify(studentEmailChanger).changeByStaff(5L, student.getPerson(), "otro@example.com", 77L);
        verify(personRepository).save(eq(student.getPerson()));
    }

    @Test
    void missingOrBlankEmailDoesNotTouchTheEmailChanger() {
        transaction.update(5L, request("30111222", null), true);
        transaction.update(5L, request("30111222", "  "), true);

        verifyNoInteractions(studentEmailChanger);
    }

    @Test
    void emailChangerRejectionPreventsSavingTheStudent() {
        doThrow(new StudentEmailChangeGoogleAccountException(5L)).when(studentEmailChanger)
                .changeByStaff(any(), any(), any(), any());

        assertThatThrownBy(() -> transaction.update(5L, request("30111222", "otro@example.com"), true))
                .isInstanceOf(StudentEmailChangeGoogleAccountException.class);
        verify(personRepository, never()).save(any());
    }

    @Test
    void unknownStudentIsRejected() {
        assertThatThrownBy(() -> transaction.update(404L, request("30111222", null), true))
                .isInstanceOf(StudentNotFoundException.class);
    }
}
