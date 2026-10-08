package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    public record AdmissionInvoiceSummary(Long id, Money total, LocalDate dueDate, String paymentStatus) {
        static AdmissionInvoiceSummary from(Invoice invoice) {
            return new AdmissionInvoiceSummary(invoice.getId(), invoice.getAmounts().total().value(), invoice.getDueDate(),
                    invoice.getPaymentStatus() == null ? null : invoice.getPaymentStatus().name());
        }
    }

    public static ProgramEnrollmentApplicationResponse from(ProgramEnrollmentApplication application) {
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
                application.getAdmissionInvoice() == null ? null : AdmissionInvoiceSummary.from(application.getAdmissionInvoice()));
    }
}
