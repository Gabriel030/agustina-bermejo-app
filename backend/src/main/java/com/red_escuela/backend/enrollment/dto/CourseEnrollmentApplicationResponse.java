package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;

public record CourseEnrollmentApplicationResponse(
        Long id,
        Long studentId,
        Long courseId,
        String courseName,
        String paymentStatus,
        String rejectionReason,
        Long courseEnrollmentId,
        String courseEnrollmentStatus) {
    public static CourseEnrollmentApplicationResponse from(CourseEnrollmentApplication application) {
        return new CourseEnrollmentApplicationResponse(
                application.getId(),
                application.getStudent().getId(),
                application.getCourse().getId(),
                application.getCourse().getName(),
                application.getPaymentReceipt() == null ? "APPROVED" : application.getPaymentReceipt().getStatus().name(),
                application.getPaymentReceipt() == null ? null : application.getPaymentReceipt().getRejectionReason(),
                application.getCourseEnrollment() == null ? null : application.getCourseEnrollment().getId(),
                application.getCourseEnrollment() == null ? null : application.getCourseEnrollment().getStatus().name());
    }
}
