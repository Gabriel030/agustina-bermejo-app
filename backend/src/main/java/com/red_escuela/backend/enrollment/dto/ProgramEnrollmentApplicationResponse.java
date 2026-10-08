package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.billing.api.AdmissionReceiptSummary;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Function;

public record ProgramEnrollmentApplicationResponse(
        Long id,
        Long studentId,
        Long programId,
        String programName,
        Long programCohortId,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt,
        String rejectionReason,
        AdmissionInvoiceSummary admissionInvoice) {

    public record AdmissionInvoiceSummary(Long id, Money total, LocalDate dueDate, String paymentStatus, String receiptStatus,
                                          LocalDateTime receiptUploadedAt) {
        static AdmissionInvoiceSummary from(Invoice invoice, AdmissionReceiptSummary latestReceipt) {
            return new AdmissionInvoiceSummary(invoice.getId(), invoice.getAmounts().total().value(), invoice.getDueDate(),
                    invoice.getPaymentStatus() == null ? null : invoice.getPaymentStatus().name(),
                    latestReceipt == null ? null : latestReceipt.status(),
                    latestReceipt == null ? null : latestReceipt.uploadedAt());
        }
    }

    public static ProgramEnrollmentApplicationResponse from(ProgramEnrollmentApplication application) {
        return from(application, invoiceId -> null);
    }

    public static ProgramEnrollmentApplicationResponse from(ProgramEnrollmentApplication application, Function<Long, AdmissionReceiptSummary> latestReceiptByInvoiceId) {
        return new ProgramEnrollmentApplicationResponse(
                application.getId(),
                application.getStudent().getId(),
                application.getProgram().getId(),
                application.getProgram().getName(),
                application.getProgramCohort() == null ? null : application.getProgramCohort().getId(),
                application.getStatus().name(),
                application.getRequestedAt(),
                application.getDecidedAt(),
                application.getRejectionReason(),
                application.getAdmissionInvoice() == null ? null : AdmissionInvoiceSummary.from(application.getAdmissionInvoice(),
                        latestReceiptByInvoiceId.apply(application.getAdmissionInvoice().getId())));
    }
}
