package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.shared.domain.Percentage;

import java.time.LocalDateTime;
import java.util.Optional;

public record MyCourseEnrollmentResponse(
        Long id, Long studentId, Long courseId, String courseName, String status,
        LocalDateTime enrollmentDate, LocalDateTime completionDate, Percentage progressPercent) {

    public static MyCourseEnrollmentResponse from(CourseEnrollment courseEnrollment, Optional<Percentage> progress) {
        CourseEnrollmentResponse base = CourseEnrollmentResponse.from(courseEnrollment);
        return new MyCourseEnrollmentResponse(
                base.id(), base.studentId(), base.courseId(), base.courseName(), base.status(),
                base.enrollmentDate(), base.completionDate(), progress.orElse(null));
    }
}
