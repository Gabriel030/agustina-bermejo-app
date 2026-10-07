package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.YearMonth;

public record InvoiceIssuingRequest(InvoiceRecipient target, FeeType feeType, YearMonth period, LocalDate dueDate, InvoiceIssuingReason reason,
                            PaymentPlanSelection selection, Long templateId, Integer slot, boolean entry, Money explicitAmount,
                            String description, Subject subject, ExamBoardRegistration examBoardRegistration) {
}
