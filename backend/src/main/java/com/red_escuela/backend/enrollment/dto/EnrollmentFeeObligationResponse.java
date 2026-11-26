package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.api.AdvanceEnrollmentFeeView;
import com.red_escuela.backend.enrollment.api.EnrollmentFeeObligationView;

import com.red_escuela.backend.shared.domain.Money;

import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.time.YearMonth;

public record EnrollmentFeeObligationResponse(Long templateId, int slot, int slotCount, YearMonth periodFrom, YearMonth periodTo, String status, Long invoiceId,
                                              LocalDate dueDate, Money remainingBalance, boolean satisfied, @Nullable AdvanceEnrollmentFeeView advance) {


    public static EnrollmentFeeObligationResponse from(EnrollmentFeeObligationView obligation) {
        return new EnrollmentFeeObligationResponse(obligation.templateId(), obligation.slot(), obligation.slotCount(), obligation.periodFrom(), obligation.periodTo(),
                obligation.status(), obligation.invoiceId(), obligation.dueDate(), obligation.remainingBalance(), obligation.satisfied(),
                obligation.advance());
    }
}
