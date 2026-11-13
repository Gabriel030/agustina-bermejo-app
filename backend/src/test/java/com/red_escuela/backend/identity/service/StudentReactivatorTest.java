package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.domain.StudentStatusChangeRecorder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.events.StudentReactivatedEvent;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.exception.StudentNotWithdrawnException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.StatusType;
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

class StudentReactivatorTest {

    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final StudentStatusChangeRecorder recorder = mock(StudentStatusChangeRecorder.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final com.red_escuela.backend.identity.infrastructure.AccountAccessStateCache accountAccessStateCache = org.mockito.Mockito.mock(com.red_escuela.backend.identity.infrastructure.AccountAccessStateCache.class);
    private final StudentReactivator service = new StudentReactivator(studentRepository, recorder, eventPublisher, accountAccessStateCache);

    private Student student(StatusType status) {
        Person person = new Person();
        person.setId(7L);
        person.setStatus(status);
        Student student = new Student();
        student.setId(3L);
        student.setPerson(person);
        return student;
    }

    @Test
    void reactivatingWithdrawnStudentRestoresActiveRecordsChangeAndPublishesEvent() {
        Student student = student(StatusType.DELETED);
        when(studentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(student));

        service.reactivate(3L, "fue una baja por error");

        assertThat(student.getPerson().getStatus()).isEqualTo(StatusType.ACTIVE);
        verify(recorder).recordReactivation(student, "fue una baja por error");
        ArgumentCaptor<StudentReactivatedEvent> event = ArgumentCaptor.forClass(StudentReactivatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().studentId()).isEqualTo(3L);
        assertThat(event.getValue().personId()).isEqualTo(7L);
    }

    @Test
    void reactivatingActiveStudentFailsWithoutAuditOrEvent() {
        Student student = student(StatusType.ACTIVE);
        when(studentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.reactivate(3L, "fue una baja por error")).isInstanceOf(StudentNotWithdrawnException.class);

        verify(recorder, never()).recordReactivation(any(), any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void reactivatingUnknownStudentFailsWithNotFound() {
        when(studentRepository.findByIdForUpdate(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reactivate(3L, "fue una baja por error")).isInstanceOf(StudentNotFoundException.class);
    }
}
