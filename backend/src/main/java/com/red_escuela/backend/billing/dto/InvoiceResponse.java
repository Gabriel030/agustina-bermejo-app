package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.red_escuela.backend.dto.ProductRef;

// fiscalInvoiceStatus stays null until Fase 6.8. The three cancellation fields stay null unless
// the invoice was actually cancelled.
public record InvoiceResponse(
        Long id, String feeType, String period, LocalDate dueDate, BigDecimal baseAmount,
        BigDecimal amountDue, BigDecimal remainingBalance, String status,
        List<InvoiceAdjustmentResponse> adjustments, String fiscalInvoiceStatus,
        LocalDateTime createdAt, LocalDateTime updatedAt,
        String cancelledByName, String cancelledReason, LocalDateTime cancelledAt,
        Long studentId, String studentDni, ProductRef productRef,
        String description, Long subjectId, Long examBoardRegistrationId) {

    public static InvoiceResponse from(Invoice invoice) {
        Long studentId = null;
        String studentDni = null;
        ProductRef productRef = null;
        CourseEnrollment courseEnrollment = invoice.getCourseEnrollment();
        ProgramEnrollment programEnrollment = invoice.getProgramEnrollment();
        if (courseEnrollment != null) {
            studentId = courseEnrollment.getStudent().getId();
            studentDni = courseEnrollment.getStudent().getPerson().getDni();
            productRef = new ProductRef("COURSE", courseEnrollment.getCourse().getId(), courseEnrollment.getId());
        } else if (programEnrollment != null) {
            studentId = programEnrollment.getStudent().getId();
            studentDni = programEnrollment.getStudent().getPerson().getDni();
            productRef = new ProductRef("PROGRAM", programEnrollment.getProgram().getId(), programEnrollment.getId());
        }
        return new InvoiceResponse(
                invoice.getId(), invoice.getFeeType().name(), invoice.getPeriod().toString(), invoice.getDueDate(),
                invoice.getBaseAmount(), invoice.getAmountDue(), invoice.getRemainingBalance(), invoice.getStatus().name(),
                invoice.getAdjustments().stream().map(InvoiceAdjustmentResponse::from).toList(),
                null,
                invoice.getCreatedAt(), invoice.getUpdatedAt(),
                invoice.getCancelledBy() == null ? null : invoice.getCancelledBy().getName() + " " + invoice.getCancelledBy().getLastname(),
                invoice.getCancelledReason(), invoice.getCancelledAt(),
                studentId, studentDni, productRef,
                invoice.getDescription(),
                invoice.getSubject() == null ? null : invoice.getSubject().getId(),
                invoice.getExamBoardRegistration() == null ? null : invoice.getExamBoardRegistration().getId());
    }
}
