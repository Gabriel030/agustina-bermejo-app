package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.billing.service.EnrollmentFeeRequirementService;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.YearMonth;

public record EnrollmentFeeObligationResponse(Long templateId, int slot, int slotCount, YearMonth periodFrom, YearMonth periodTo, String status, Long invoiceId,
                                              LocalDate dueDate, Money remainingBalance, boolean satisfied) {

    public static EnrollmentFeeObligationResponse from(EnrollmentFeeRequirementService.Obligation obligation) {
        return new EnrollmentFeeObligationResponse(obligation.templateId(), obligation.slot(), obligation.slotCount(), obligation.periodFrom(), obligation.periodTo(),
                obligation.status().name(), obligation.invoiceId(), obligation.dueDate(), obligation.remainingBalance(), obligation.satisfied());
    }
}
