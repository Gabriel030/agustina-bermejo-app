package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.service.InvoiceWithReceipts;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingView;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;

import java.time.LocalDateTime;
import java.util.List;

public record AdminEnrollmentBillingStatusResponse(String paymentStatus, LocalDateTime paymentStatusUpdatedAt,
                   List<AdminInvoiceResponse> invoices,
                   String enrollmentType, Long enrollmentId,
                   String productName, Long productId) {

    public static AdminEnrollmentBillingStatusResponse of(CourseEnrollment enrollment, EnrollmentPaymentStandingView standing, List<InvoiceWithReceipts> invoices) {
        return new AdminEnrollmentBillingStatusResponse(
                EnrollmentPaymentStandingView.statusOrUpToDate(standing).name(), EnrollmentPaymentStandingView.statusChangedAtOrNull(standing),
                invoices.stream().map(i -> AdminInvoiceResponse.of(i.view(), i.receipts(), i.pendingAuthorizations())).toList(),
                "COURSE", enrollment.getId(), enrollment.getCourse().getName(), enrollment.getCourse().getId());
    }

    public static AdminEnrollmentBillingStatusResponse of(ProgramEnrollment enrollment, EnrollmentPaymentStandingView standing, List<InvoiceWithReceipts> invoices) {
        return new AdminEnrollmentBillingStatusResponse(
                EnrollmentPaymentStandingView.statusOrUpToDate(standing).name(), EnrollmentPaymentStandingView.statusChangedAtOrNull(standing),
                invoices.stream().map(i -> AdminInvoiceResponse.of(i.view(), i.receipts(), i.pendingAuthorizations())).toList(),
                "PROGRAM", enrollment.getId(), enrollment.getProgram().getName(), enrollment.getProgram().getId());
    }
}
