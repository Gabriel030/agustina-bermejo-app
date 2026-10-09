package com.red_escuela.backend.billing.api;

public interface CourseInvoiceEnrollmentLink {
    void linkInvoiceToCourseEnrollment(Long invoiceId, Long courseEnrollmentId);
}
