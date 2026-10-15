package com.red_escuela.backend.notification;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceChangeRequestDecisionEventTest {

    private AttendanceChangeRequest changeRequest(AttendanceStatus from, AttendanceStatus to) {
        Person person = new Person();
        person.setName("Benjamín");
        person.setLastname("Coronel");
        Student student = new Student();
        student.setPerson(person);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        Attendance attendance = new Attendance();
        attendance.setProgramSubjectSectionEnrollment(enrollment);
        attendance.setClassDate(LocalDate.of(2026, 10, 12));
        AttendanceChangeRequest request = new AttendanceChangeRequest();
        request.setAttendance(attendance);
        request.setPreviousStatus(from);
        request.setRequestedStatus(to);
        return request;
    }

    @Test
    void approvedMessageNamesTheStudentTheClassDateAndTranslatedStatuses() {
        AttendanceChangeRequestApprovedEvent event = new AttendanceChangeRequestApprovedEvent(
                changeRequest(AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFIED), new Person());

        assertThat(event.message()).isEqualTo(
                "Tu solicitud de modificación de asistencia de Benjamín Coronel del 12/10/2026 fue aprobada: Ausente → Justificada");
    }

    @Test
    void rejectedMessageNamesTheStudentTheClassDateAndTranslatedStatuses() {
        AttendanceChangeRequestRejectedEvent event = new AttendanceChangeRequestRejectedEvent(
                changeRequest(AttendanceStatus.PRESENT, AttendanceStatus.LATE), new Person());

        assertThat(event.message()).isEqualTo(
                "Tu solicitud de modificación de asistencia de Benjamín Coronel del 12/10/2026 fue rechazada: Presente → Tarde");
    }
}
