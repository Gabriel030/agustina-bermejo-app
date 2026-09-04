package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.BillingPeriod;

import java.time.LocalDate;

public record BillingPeriodResponse(Long id, Integer startDay, Integer endDay, LocalDate effectiveFrom) {

    public static BillingPeriodResponse from(BillingPeriod billingPeriod) {
        return new BillingPeriodResponse(billingPeriod.getId(), billingPeriod.getStartDay(), billingPeriod.getEndDay(), billingPeriod.getEffectiveFrom());
    }
}
