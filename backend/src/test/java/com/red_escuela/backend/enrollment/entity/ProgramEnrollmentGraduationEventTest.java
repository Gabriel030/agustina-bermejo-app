package com.red_escuela.backend.enrollment.entity;

import com.red_escuela.backend.enrollment.events.ProgramEnrollmentGraduatedEvent;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-236")
@Tag("D-296")
class ProgramEnrollmentGraduationEventTest {

    private ProgramEnrollment activeEnrollment() {
        Student student = new Student();
        student.setId(41L);
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setId(10L);
        enrollment.setStudent(student);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        return enrollment;
    }

    @Test
    void completingAnEnrollmentRegistersTheGraduationEventWithTheCompletionDate() {
        ProgramEnrollment enrollment = activeEnrollment();

        enrollment.complete(LocalDateTime.of(2027, 10, 20, 10, 0));

        assertThat(enrollment.publishGraduationEvents()).singleElement().isInstanceOfSatisfying(ProgramEnrollmentGraduatedEvent.class, event -> {
            assertThat(event.studentId()).isEqualTo(41L);
            assertThat(event.programEnrollmentId()).isEqualTo(10L);
            assertThat(event.effectiveDate()).isEqualTo(LocalDate.of(2027, 10, 20));
        });
    }

    @Test
    void publishedGraduationEventsAreClearedAfterThePublication() {
        ProgramEnrollment enrollment = activeEnrollment();
        enrollment.complete(LocalDateTime.of(2027, 10, 20, 10, 0));

        enrollment.clearGraduationEvents();

        assertThat(enrollment.publishGraduationEvents()).isEmpty();
    }

    @Test
    void completingAnInactiveEnrollmentRegistersNoEvent() {
        ProgramEnrollment enrollment = activeEnrollment();
        enrollment.setStatus(ProgramEnrollmentStatus.WITHDRAWN);

        assertThatThrownBy(() -> enrollment.complete(LocalDateTime.of(2027, 10, 20, 10, 0))).isInstanceOf(RuntimeException.class);

        assertThat(enrollment.publishGraduationEvents()).isEmpty();
    }
}
