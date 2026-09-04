package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.PaymentPlanSelection;

import java.time.YearMonth;

public record PaymentPlanSelectionResponse(Long id, Long paymentPlanOptionId, YearMonth effectiveFrom, YearMonth effectiveTo, Integer scopeMonths) {

    public static PaymentPlanSelectionResponse from(PaymentPlanSelection selection) {
        return new PaymentPlanSelectionResponse(
                selection.getId(), selection.getPaymentPlanOption().getId(),
                selection.getEffectiveFrom(), selection.getEffectiveTo(), selection.getScopeMonths());
    }
}
