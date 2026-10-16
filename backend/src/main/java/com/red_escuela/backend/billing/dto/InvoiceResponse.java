package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.invoice.dto.InvoiceLineResponse;
import com.red_escuela.backend.billing.invoice.service.InvoicePaymentShare;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceAmounts;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import com.red_escuela.backend.billing.invoice.service.InvoiceView;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record InvoiceResponse(
        Long id, String invoiceNumber, String invoiceType, String documentType, String documentStatus,
        String paymentStatus, LocalDate issueDate, LocalDate dueDate, String currency,
        Money linesTotalAmount, Money discountsTotalAmount, Money totalAmount, Money paidAmount,
        Money creditedAmount, Money outstandingAmount, List<InvoiceLineResponse> lines,
        String issuingReason, String discardedReason,
        Long studentId, String studentDni, ProductRef productRef,
        String description, Long subjectId, Long examBoardRegistrationId,
        LocalDateTime paymentDate, String paymentMethod) {

    public static InvoiceResponse from(InvoiceView view) {
        Invoice invoice = view.invoice();
        InvoiceLine line = invoice.getPrimaryLine();
        Long studentId = null;
        String studentDni = null;
        ProductRef productRef = null;
        CourseEnrollment courseEnrollment = view.courseEnrollment();
        ProgramEnrollment programEnrollment = view.programEnrollment();
        if (courseEnrollment != null) {
            studentId = courseEnrollment.getStudent().getId();
            studentDni = courseEnrollment.getStudent().getPerson().getDni();
            productRef = new ProductRef("COURSE", courseEnrollment.getCourse().getId(), courseEnrollment.getId());
        } else if (programEnrollment != null) {
            studentId = programEnrollment.getStudent().getId();
            studentDni = programEnrollment.getStudent().getPerson().getDni();
            productRef = new ProductRef("PROGRAM", programEnrollment.getProgram().getId(), programEnrollment.getId());
        }
        InvoicePaymentShare latest = view.paymentShares().stream().max(Comparator.comparing(InvoicePaymentShare::paidAt)).orElse(null);
        InvoiceAmounts amounts = invoice.getAmounts();
        return new InvoiceResponse(
                invoice.getId(), invoice.getInvoiceNumber() == null ? null : invoice.getInvoiceNumber().value(),
                invoice.getInvoiceType().name(), invoice.getDocumentType().name(), invoice.getDocumentStatus().name(),
                invoice.getPaymentStatus() == null ? null : invoice.getPaymentStatus().name(), invoice.getIssueDate(), invoice.getDueDate(), invoice.currency().name(),
                amounts.linesTotal().value(), amounts.discountsTotal().value(), amounts.total().value(), amounts.paidAmount().value(),
                amounts.creditedAmount().value(), amounts.outstandingAmount().value(),
                invoice.getLines().stream().map(InvoiceLineResponse::from).toList(),
                invoice.getIssuingReason().name(), invoice.getDiscardedReason(),
                studentId, studentDni, productRef,
                line.getConcept(), line.getSubjectId(), line.getExamBoardRegistrationId(),
                latest == null ? null : latest.paidAt(), latest == null ? null : latest.method());
    }
}
