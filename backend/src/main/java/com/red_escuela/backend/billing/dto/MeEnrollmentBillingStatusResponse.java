package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.service.InvoiceWithReceipts;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingView;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;

import java.time.LocalDateTime;
import java.util.List;

public record MeEnrollmentBillingStatusResponse(String paymentStatus, LocalDateTime paymentStatusUpdatedAt,
                   List<MeInvoiceResponse> invoices,
                   String enrollmentType, Long enrollmentId,
                   String productName, Long productId) {

    public static MeEnrollmentBillingStatusResponse of(CourseEnrollment enrollment, EnrollmentPaymentStandingView standing, List<InvoiceWithReceipts> invoices) {
        return new MeEnrollmentBillingStatusResponse(
                EnrollmentPaymentStandingView.statusOrUpToDate(standing).name(), EnrollmentPaymentStandingView.statusChangedAtOrNull(standing),
                invoices.stream().map(i -> MeInvoiceResponse.of(i.view(), i.receipts())).toList(),
                "COURSE", enrollment.getId(), enrollment.getCourse().getName(), enrollment.getCourse().getId());
    }

    public static MeEnrollmentBillingStatusResponse of(ProgramEnrollment enrollment, EnrollmentPaymentStandingView standing, List<InvoiceWithReceipts> invoices) {
        return new MeEnrollmentBillingStatusResponse(
                EnrollmentPaymentStandingView.statusOrUpToDate(standing).name(), EnrollmentPaymentStandingView.statusChangedAtOrNull(standing),
                invoices.stream().map(i -> MeInvoiceResponse.of(i.view(), i.receipts())).toList(),
                "PROGRAM", enrollment.getId(), enrollment.getProgram().getName(), enrollment.getProgram().getId());
    }
}
