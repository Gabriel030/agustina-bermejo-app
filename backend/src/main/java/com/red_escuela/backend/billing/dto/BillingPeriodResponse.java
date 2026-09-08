package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.BillingPeriod;

import java.time.LocalDate;

public record BillingPeriodResponse(Long id, Integer startDay, Integer endDay, LocalDate effectiveFrom) {

    public static BillingPeriodResponse from(BillingPeriod billingPeriod) {
        return new BillingPeriodResponse(billingPeriod.getId(), billingPeriod.getStartDay(), billingPeriod.getEndDay(), billingPeriod.getEffectiveFrom());
    }
}
