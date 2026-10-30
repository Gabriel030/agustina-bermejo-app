package com.red_escuela.backend.attendance.events;

import com.red_escuela.backend.attendance.entity.Attendance;

public record AttendanceStatusChangedEvent(Long programSectionEnrollmentId, Long courseSectionEnrollmentId) {
    public static AttendanceStatusChangedEvent of(Attendance attendance) {
        return new AttendanceStatusChangedEvent(
                attendance.getProgramSubjectSectionEnrollment() == null ? null : attendance.getProgramSubjectSectionEnrollment().getId(),
                attendance.getCourseSubjectSectionEnrollment() == null ? null : attendance.getCourseSubjectSectionEnrollment().getId());
    }
}
