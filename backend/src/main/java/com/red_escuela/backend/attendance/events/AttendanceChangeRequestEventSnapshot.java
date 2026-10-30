package com.red_escuela.backend.attendance.events;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.identity.entity.Person;

import java.util.Map;

final class AttendanceChangeRequestEventSnapshot {

    private static final Map<AttendanceStatus, String> STATUS_LABELS = Map.of(
            AttendanceStatus.PRESENT, "Presente",
            AttendanceStatus.ABSENT, "Ausente",
            AttendanceStatus.LATE, "Tarde",
            AttendanceStatus.JUSTIFICATION_PENDING, "Justificación pendiente",
            AttendanceStatus.JUSTIFIED, "Justificada");

    private AttendanceChangeRequestEventSnapshot() {
    }

    static String studentFullNameOf(AttendanceChangeRequest request) {
        Attendance attendance = request.getAttendance();
        Person student = attendance.getProgramSubjectSectionEnrollment() != null
                ? attendance.getProgramSubjectSectionEnrollment().getStudent().getPerson()
                : attendance.getCourseSubjectSectionEnrollment().getStudent().getPerson();
        return student.getName() + " " + student.getLastname();
    }

    static String labelOf(AttendanceStatus status) {
        return STATUS_LABELS.get(status);
    }
}
