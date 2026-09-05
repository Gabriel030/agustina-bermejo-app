package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.MonetaryAdjustmentValue;
import com.red_escuela.backend.entity.Scholarship;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ScholarshipResponse(
        Long id, String valueType, BigDecimal valueAmount, String reason, LocalDate validFrom, LocalDate validUntil,
        String grantedByName, LocalDateTime grantedAt) {

    public static ScholarshipResponse from(Scholarship scholarship) {
        MonetaryAdjustmentValue value = scholarship.getValue();
        String valueType = value instanceof MonetaryAdjustmentValue.FixedAmount ? "FIXED" : "PERCENTAGE";
        BigDecimal valueAmount = value instanceof MonetaryAdjustmentValue.FixedAmount fixed
                ? fixed.amount()
                : ((MonetaryAdjustmentValue.PercentageAmount) value).percentage();
        return new ScholarshipResponse(
                scholarship.getId(), valueType, valueAmount, scholarship.getReason(), scholarship.getValidFrom(),
                scholarship.getValidUntil(), scholarship.getGrantedBy().getName() + " " + scholarship.getGrantedBy().getLastname(),
                scholarship.getGrantedAt());
    }
}
