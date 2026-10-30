package com.red_escuela.backend.notification;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.attendance.events.AttendanceChangeRequestApprovedEvent;
import com.red_escuela.backend.attendance.events.AttendanceChangeRequestRejectedEvent;
import com.red_escuela.backend.academicstructure.entity.Instructor;
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
        Person requesterPerson = new Person();
        requesterPerson.setId(77L);
        Instructor requester = new Instructor();
        requester.setPerson(requesterPerson);
        request.setId(5L);
        request.setRequestedBy(requester);
        request.setAttendance(attendance);
        request.setPreviousStatus(from);
        request.setRequestedStatus(to);
        return request;
    }

    @Test
    void approvedMessageNamesTheStudentTheClassDateAndTranslatedStatuses() {
        AttendanceChangeRequestApprovedEvent source = AttendanceChangeRequestApprovedEvent.approvedFor(
                changeRequest(AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFIED));
        AttendanceChangeRequestApprovedNotification event = new AttendanceChangeRequestApprovedNotification(source.requestId(), new Person(),
                source.studentFullName(), source.classDate(), source.previousStatusLabel(), source.requestedStatusLabel());

        assertThat(event.message()).isEqualTo(
                "Tu solicitud de modificación de asistencia de Benjamín Coronel del 12/10/2026 fue aprobada: Ausente → Justificada");
    }

    @Test
    void rejectedMessageNamesTheStudentTheClassDateAndTranslatedStatuses() {
        AttendanceChangeRequestRejectedEvent source = AttendanceChangeRequestRejectedEvent.rejectedFor(
                changeRequest(AttendanceStatus.PRESENT, AttendanceStatus.LATE));
        AttendanceChangeRequestRejectedNotification event = new AttendanceChangeRequestRejectedNotification(source.requestId(), new Person(),
                source.studentFullName(), source.classDate(), source.previousStatusLabel(), source.requestedStatusLabel());

        assertThat(event.message()).isEqualTo(
                "Tu solicitud de modificación de asistencia de Benjamín Coronel del 12/10/2026 fue rechazada: Presente → Tarde");
    }
}
