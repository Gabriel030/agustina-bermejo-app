package com.red_escuela.backend.billing.planning.domain;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;

import java.time.YearMonth;

public record PaymentPlanCoverage(PaymentPlanCadence cadence, YearMonth effectiveFrom, Integer scopeMonths) {

    public static PaymentPlanCoverage none() {
        return new PaymentPlanCoverage(null, null, null);
    }

    public static PaymentPlanCoverage ofCadence(PaymentPlanCadence cadence) {
        return new PaymentPlanCoverage(cadence, null, null);
    }

    public boolean isAnnualScopeExpiredAt(YearMonth month) {
        return cadence == PaymentPlanCadence.ANNUAL_UPFRONT && scopeMonths != null && effectiveFrom != null
                && month.isAfter(effectiveFrom.plusMonths(scopeMonths - 1L));
    }

    public PaymentPlanCadence effectiveCadenceForMonth(YearMonth month) {
        return isAnnualScopeExpiredAt(month) ? PaymentPlanCadence.MONTHLY : cadence;
    }
}
