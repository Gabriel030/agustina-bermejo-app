package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.billing.api.AdmissionReceiptSummary;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.identity.entity.Person;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

public record ProgramEnrollmentApplicationResponse(
        Long id,
        Long studentId,
        Long programId,
        String programName,
        Long programCohortId,
        String status,
        LocalDateTime requestedAt,
        @Schema(nullable = true) LocalDateTime decidedAt,
        @Schema(nullable = true) String rejectionReason,
        @Schema(nullable = true) AdmissionInvoiceSummary admissionInvoice,
        Long programEnrollmentId,
        @Schema(nullable = true) String studentFullName,
        @Schema(nullable = true) String studentEmail,
        @Schema(nullable = true) String studentDni,
        @Schema(nullable = true) String programCohortName,
        @Schema(nullable = true) String decidedByName,
        @Schema(nullable = true) DocumentationSummary documentation) {

    public record AdmissionInvoiceSummary(Long id, Money total, LocalDate dueDate, String paymentStatus, String receiptStatus,
                                          LocalDateTime receiptUploadedAt) {
        static AdmissionInvoiceSummary from(Invoice invoice, AdmissionReceiptSummary latestReceipt) {
            return new AdmissionInvoiceSummary(invoice.getId(), invoice.getAmounts().total().value(), invoice.getDueDate(),
                    invoice.getPaymentStatus() == null ? null : invoice.getPaymentStatus().name(),
                    latestReceipt == null ? null : latestReceipt.status(),
                    latestReceipt == null ? null : latestReceipt.uploadedAt());
        }
    }

    public record DocumentationSummary(boolean complete, List<DocumentType> missingTypes, List<StudentDocumentResponse> documents) {
    }

    public static ProgramEnrollmentApplicationResponse from(ProgramEnrollmentApplication application) {
        return from(application, invoiceId -> null);
    }

    public static ProgramEnrollmentApplicationResponse from(ProgramEnrollmentApplication application, Function<Long, AdmissionReceiptSummary> latestReceiptByInvoiceId) {
        return from(application, latestReceiptByInvoiceId, applicationId -> null);
    }

    public static ProgramEnrollmentApplicationResponse from(ProgramEnrollmentApplication application, Function<Long, AdmissionReceiptSummary> latestReceiptByInvoiceId,
                                                            Function<Long, Long> enrollmentIdByApplicationId) {
        return build(application, latestReceiptByInvoiceId, enrollmentIdByApplicationId, null, null);
    }

    public static ProgramEnrollmentApplicationResponse forReview(ProgramEnrollmentApplication application, DocumentationSummary documentation,
                                                                 String cohortName) {
        return build(application, invoiceId -> null, applicationId -> null, documentation, cohortName);
    }

    private static ProgramEnrollmentApplicationResponse build(ProgramEnrollmentApplication application,
                                                              Function<Long, AdmissionReceiptSummary> latestReceiptByInvoiceId,
                                                              Function<Long, Long> enrollmentIdByApplicationId,
                                                              DocumentationSummary documentation, String cohortName) {
        boolean forReview = documentation != null;
        Person studentPerson = forReview ? application.getStudent().getPerson() : null;
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
                        latestReceiptByInvoiceId.apply(application.getAdmissionInvoice().getId())),
                enrollmentIdByApplicationId.apply(application.getId()),
                forReview ? fullNameOf(studentPerson) : null,
                forReview && studentPerson.getEmail() != null ? studentPerson.getEmail().value() : null,
                forReview ? studentPerson.getDni() : null,
                cohortName,
                forReview && application.getDecidedBy() != null ? fullNameOf(application.getDecidedBy()) : null,
                documentation);
    }

    private static String fullNameOf(Person person) {
        return person.getName() + " " + person.getLastname();
    }
}
