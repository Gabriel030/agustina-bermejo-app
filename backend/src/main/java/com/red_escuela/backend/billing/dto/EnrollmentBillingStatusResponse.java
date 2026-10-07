package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;

import java.time.LocalDateTime;
import java.util.List;

public record EnrollmentBillingStatusResponse(String paymentStatus, LocalDateTime paymentStatusUpdatedAt,
                                              List<InvoiceResponse> invoices,
                                              String enrollmentType, Long enrollmentId,
                                              String productName, Long productId) {

    public static EnrollmentBillingStatusResponse of(CourseEnrollment enrollment, List<Invoice> invoices) {
        return new EnrollmentBillingStatusResponse(
                enrollment.getPaymentStatus().name(), enrollment.getPaymentStatusUpdatedAt(),
                invoices.stream().map(InvoiceResponse::from).toList(),
                "COURSE", enrollment.getId(), enrollment.getCourse().getName(), enrollment.getCourse().getId());
    }

    public static EnrollmentBillingStatusResponse of(ProgramEnrollment enrollment, List<Invoice> invoices) {
        return new EnrollmentBillingStatusResponse(
                enrollment.getPaymentStatus().name(), enrollment.getPaymentStatusUpdatedAt(),
                invoices.stream().map(InvoiceResponse::from).toList(),
                "PROGRAM", enrollment.getId(), enrollment.getProgram().getName(), enrollment.getProgram().getId());
    }
}
