package com.red_escuela.backend.enrollment.api;

public interface CourseAdmissionInvoicePort {

    CourseAdmissionInvoiceView issueCourseAdmissionInvoice(Long studentId, Long courseId);

    void assignInvoiceToCourseEnrollment(Long invoiceId, Long courseEnrollmentId);
}
