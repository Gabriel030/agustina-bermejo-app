package com.red_escuela.backend.notification;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.events.AttendanceJustificationApprovedEvent;
import com.red_escuela.backend.attendance.events.AttendanceJustificationRejectedEvent;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceJustificationDecisionEventTest {

    private Attendance attendance() {
        Person person = new Person();
        person.setId(41L);
        Student student = new Student();
        student.setPerson(person);
        Subject subject = new Subject();
        subject.setName("Matemática");
        SubjectSection section = new SubjectSection();
        section.setSubject(subject);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setSubjectSection(section);
        Attendance attendance = new Attendance();
        attendance.setId(9L);
        attendance.setProgramSubjectSectionEnrollment(enrollment);
        attendance.setClassDate(LocalDate.of(2026, 10, 12));
        attendance.setJustificationRejectionReason("Comprobante ilegible");
        return attendance;
    }

    @Test
    void approvedNotificationNamesTheClassDateAndTheSubjectAndTargetsTheStudent() {
        AttendanceJustificationApprovedEvent source = AttendanceJustificationApprovedEvent.approvedFor(attendance());
        Person student = new Person();
        AttendanceJustificationApprovedNotification event = new AttendanceJustificationApprovedNotification(
                source.attendanceId(), student, source.classDate(), source.subjectName());

        assertThat(source.studentPersonId()).isEqualTo(41L);
        assertThat(event.type()).isEqualTo(NotificationType.ATTENDANCE_JUSTIFICATION_APPROVED);
        assertThat(event.message()).isEqualTo("Tu justificación de la inasistencia del 12/10/2026 en Matemática fue aprobada");
        assertThat(event.recipients()).containsExactly(new PersonRecipient(student));
        assertThat(event.relatedEntityId()).isEqualTo(9L);
    }

    @Test
    void rejectedNotificationIncludesTheRejectionReason() {
        AttendanceJustificationRejectedEvent source = AttendanceJustificationRejectedEvent.rejectedFor(attendance());
        AttendanceJustificationRejectedNotification event = new AttendanceJustificationRejectedNotification(
                source.attendanceId(), new Person(), source.classDate(), source.subjectName(), source.rejectionReason());

        assertThat(event.type()).isEqualTo(NotificationType.ATTENDANCE_JUSTIFICATION_REJECTED);
        assertThat(event.message()).isEqualTo(
                "Tu justificación de la inasistencia del 12/10/2026 en Matemática fue rechazada. Motivo: Comprobante ilegible");
    }
}
