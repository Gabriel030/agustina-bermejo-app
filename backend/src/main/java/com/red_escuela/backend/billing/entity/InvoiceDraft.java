package com.red_escuela.backend.billing.entity;

import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.YearMonth;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;

public record InvoiceDraft(ProgramEnrollment programEnrollment, CourseEnrollment courseEnrollment, PaymentPlanSelection selection,
                           FeeType feeType, YearMonth period, LocalDate dueDate, Money baseAmount, InvoiceIssuingReason reason,
                           PriceRevision priceRevision, Long templateId, Integer enrollmentFeeSlot, String description,
                           Subject subject, ExamBoardRegistration examBoardRegistration) {
}
