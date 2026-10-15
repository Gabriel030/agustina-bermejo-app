package com.red_escuela.backend.notification;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.identity.entity.Person;

import java.time.format.DateTimeFormatter;
import java.util.Map;

final class AttendanceChangeRequestMessage {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Map<AttendanceStatus, String> STATUS_LABELS = Map.of(
            AttendanceStatus.PRESENT, "Presente",
            AttendanceStatus.ABSENT, "Ausente",
            AttendanceStatus.LATE, "Tarde",
            AttendanceStatus.JUSTIFICATION_PENDING, "Justificación pendiente",
            AttendanceStatus.JUSTIFIED, "Justificada");

    private AttendanceChangeRequestMessage() {
    }

    static String of(AttendanceChangeRequest request, String decision) {
        Attendance attendance = request.getAttendance();
        Person student = attendance.getProgramSubjectSectionEnrollment() != null
                ? attendance.getProgramSubjectSectionEnrollment().getStudent().getPerson()
                : attendance.getCourseSubjectSectionEnrollment().getStudent().getPerson();
        return "Tu solicitud de modificación de asistencia de " + student.getName() + " " + student.getLastname()
                + " del " + attendance.getClassDate().format(DAY_FORMAT) + " fue " + decision + ": "
                + STATUS_LABELS.get(request.getPreviousStatus()) + " → " + STATUS_LABELS.get(request.getRequestedStatus());
    }
}
