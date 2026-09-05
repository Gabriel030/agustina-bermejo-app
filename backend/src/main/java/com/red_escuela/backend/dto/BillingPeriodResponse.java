package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.BillingPeriod;

import java.time.LocalDate;

public record BillingPeriodResponse(Long id, Integer diaInicio, Integer diaFin, LocalDate vigenteDesde) {

    public static BillingPeriodResponse from(BillingPeriod period) {
        return new BillingPeriodResponse(period.getId(), period.getStartDay(), period.getEndDay(), period.getValidFrom());
    }
}
