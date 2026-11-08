package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;

import java.time.LocalDate;
import java.util.Optional;

public record CourseEnrollmentEndView(Long enrollmentId, EnrollmentStatus status, LocalDate courseEndDate) {

    public boolean isActiveAndCourseEndedBefore(LocalDate today) {
        return status == EnrollmentStatus.ENROLLED && Optional.ofNullable(courseEndDate).map(today::isAfter).orElse(false);
    }
}
