package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;

public record CourseEnrollmentApplicationResponse(
        Long id,
        Long studentId,
        Long courseId,
        String courseName,
        String paymentStatus,
        String rejectionReason,
        Long courseEnrollmentId) {

    // paymentStatus/rejectionReason salen del PaymentReceipt, no de un estado
    // propio de esta entidad — no tiene uno (ver javadoc de CourseEnrollmentApplication).
    public static CourseEnrollmentApplicationResponse from(CourseEnrollmentApplication application) {
        return new CourseEnrollmentApplicationResponse(
                application.getId(),
                application.getStudent().getId(),
                application.getCourse().getId(),
                application.getCourse().getName(),
                application.getPaymentReceipt().getStatus().name(),
                application.getPaymentReceipt().getRejectionReason(),
                application.getCourseEnrollment() == null ? null : application.getCourseEnrollment().getId());
    }
}
