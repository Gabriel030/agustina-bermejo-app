package com.red_escuela.backend.enrollment.api;

public interface CourseEnrollmentApplicationCancellationPort {

    boolean hasPendingApplicationOfInvoice(Long invoiceId);

    boolean cancelPendingApplicationOfInvoice(Long invoiceId);
}
