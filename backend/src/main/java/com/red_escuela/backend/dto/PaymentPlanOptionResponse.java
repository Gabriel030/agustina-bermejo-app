package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.PaymentPlanOption;

import java.time.LocalDate;

public record PaymentPlanOptionResponse(
        Long id, String description, String cadence, boolean upfrontWindowEnabled,
        LocalDate upfrontWindowStart, LocalDate upfrontWindowEnd) {

    public static PaymentPlanOptionResponse from(PaymentPlanOption option) {
        return new PaymentPlanOptionResponse(
                option.getId(), option.getDescription(), option.getCadence().name(),
                option.isUpfrontWindowEnabled(), option.getUpfrontWindowStart(), option.getUpfrontWindowEnd());
    }
}
