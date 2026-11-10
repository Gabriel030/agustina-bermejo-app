package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.events.GraduationRequestOutdatedEvent;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraduationRequestOutdatedFlaggerTest {

    private final GraduationRequestRepository repository = mock(GraduationRequestRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final GraduationRequestOutdatedFlagger flagger = new GraduationRequestOutdatedFlagger(repository, publisher);

    private GraduationRequest requestOf(long id) {
        Person person = new Person();
        person.setId(9L);
        person.setName("Ana");
        person.setLastname("Perez");
        Student student = new Student();
        student.setPerson(person);
        Program program = new Program();
        program.setName("Carrera");
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        GraduationRequest request = new GraduationRequest();
        request.setId(id);
        request.setProgramEnrollment(enrollment);
        return request;
    }

    @Test
    void everyNonRejectedRequestOfTheEnrollmentIsFlaggedOutdated() {
        when(repository.findByProgramEnrollmentIdAndStatusNot(5L, GraduationRequestStatus.REJECTED))
                .thenReturn(List.of(requestOf(1L)));

        flagger.flagOutdatedRequests(5L);

        verify(publisher).publishEvent(any(GraduationRequestOutdatedEvent.class));
    }

    @Test
    void nothingIsPublishedWhenTheEnrollmentHasNoOpenRequest() {
        when(repository.findByProgramEnrollmentIdAndStatusNot(5L, GraduationRequestStatus.REJECTED)).thenReturn(List.of());

        flagger.flagOutdatedRequests(5L);

        verify(publisher, never()).publishEvent(any(Object.class));
    }
}
