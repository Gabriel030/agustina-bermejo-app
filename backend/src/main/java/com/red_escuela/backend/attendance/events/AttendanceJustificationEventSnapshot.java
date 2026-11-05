package com.red_escuela.backend.attendance.events;

import com.red_escuela.backend.attendance.entity.Attendance;

final class AttendanceJustificationEventSnapshot {

    private AttendanceJustificationEventSnapshot() {
    }

    static Long studentPersonIdOf(Attendance attendance) {
        return attendance.getProgramSubjectSectionEnrollment() != null
                ? attendance.getProgramSubjectSectionEnrollment().getStudent().getPerson().getId()
                : attendance.getCourseSubjectSectionEnrollment().getStudent().getPerson().getId();
    }

    static String subjectNameOf(Attendance attendance) {
        return attendance.getProgramSubjectSectionEnrollment() != null
                ? attendance.getProgramSubjectSectionEnrollment().getSubjectSection().getSubject().getName()
                : attendance.getCourseSubjectSectionEnrollment().getCourseSubjectSection().getCourseSubject().getName();
    }
}
