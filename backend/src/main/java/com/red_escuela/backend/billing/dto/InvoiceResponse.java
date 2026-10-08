package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import com.red_escuela.backend.billing.service.InvoiceView;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record InvoiceResponse(
        Long id, String feeType, String period, LocalDate dueDate, Money baseAmount,
        Money amountDue, Money remainingBalance, String status,
        List<InvoiceAdjustmentResponse> adjustments, String fiscalInvoiceStatus,
        LocalDateTime createdAt, LocalDateTime updatedAt,
        String cancelledByName, String cancelledReason, LocalDateTime cancelledAt,
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
        PaymentAllocation latest = view.allocations().stream().max(Comparator.comparing((PaymentAllocation a) -> a.getPayment().getPaidAt())).orElse(null);
        boolean discarded = invoice.getDocumentStatus() == InvoiceDocumentStatus.DISCARDED;
        return new InvoiceResponse(
                invoice.getId(), line.getFeeType().name(), line.getPeriod().toString(), invoice.getDueDate(),
                invoice.getAmounts().linesTotal().value(), invoice.getAmounts().total().value(), invoice.getAmounts().outstandingAmount().value(),
                discarded ? "CANCELLED" : invoice.getPaymentStatus().name(),
                invoice.getLines().stream().filter(l -> l.getDiscount() != null).map(l -> InvoiceAdjustmentResponse.from(l.getDiscount())).toList(),
                null,
                invoice.getCreatedAt(), invoice.getUpdatedAt(),
                null, invoice.getDiscardedReason(), null,
                studentId, studentDni, productRef,
                line.getConcept(), line.getSubjectId(), line.getExamBoardRegistrationId(),
                latest == null ? null : latest.getPayment().getPaidAt(), latest == null ? null : latest.getPayment().getMethod().name());
    }
}
