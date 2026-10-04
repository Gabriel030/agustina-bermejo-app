package com.red_escuela.backend.billing.issuing;

import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.IssueReason;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.YearMonth;

public record ChargeRequest(ChargeTarget target, FeeType feeType, YearMonth period, LocalDate dueDate, IssueReason reason,
                            PaymentPlanSelection selection, Long templateId, Integer slot, boolean entry, Money explicitAmount,
                            String description, Subject subject, ExamBoardRegistration examBoardRegistration) {
}
