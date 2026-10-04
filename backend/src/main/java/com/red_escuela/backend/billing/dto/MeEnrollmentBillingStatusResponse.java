package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.service.InvoiceWithReceipts;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;

import java.time.LocalDateTime;
import java.util.List;

public record MeEnrollmentBillingStatusResponse(String billingStanding, LocalDateTime billingStandingUpdatedAt,
                   List<MeInvoiceResponse> invoices,
                   String enrollmentType, Long enrollmentId,
                   String productName, Long productId) {

    public static MeEnrollmentBillingStatusResponse of(CourseEnrollment enrollment, List<InvoiceWithReceipts> invoices) {
        return new MeEnrollmentBillingStatusResponse(
                enrollment.getBillingStanding().name(), enrollment.getBillingStandingUpdatedAt(),
                invoices.stream().map(i -> MeInvoiceResponse.of(i.invoice(), i.receipts())).toList(),
                "COURSE", enrollment.getId(), enrollment.getCourse().getName(), enrollment.getCourse().getId());
    }

    public static MeEnrollmentBillingStatusResponse of(ProgramEnrollment enrollment, List<InvoiceWithReceipts> invoices) {
        return new MeEnrollmentBillingStatusResponse(
                enrollment.getBillingStanding().name(), enrollment.getBillingStandingUpdatedAt(),
                invoices.stream().map(i -> MeInvoiceResponse.of(i.invoice(), i.receipts())).toList(),
                "PROGRAM", enrollment.getId(), enrollment.getProgram().getName(), enrollment.getProgram().getId());
    }
}
