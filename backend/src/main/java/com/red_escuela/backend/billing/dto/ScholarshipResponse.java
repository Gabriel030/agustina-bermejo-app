package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.InstallmentStatus;
import com.red_escuela.backend.billing.entity.MonetaryAdjustmentValue;
import com.red_escuela.backend.billing.entity.Scholarship;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ScholarshipResponse(
        Long id, String valueType, BigDecimal valueAmount, String reason, String status, int totalInstallments,
        int remainingInstallments, String grantedByName, LocalDateTime grantedAt) {

    public static ScholarshipResponse from(Scholarship scholarship) {
        MonetaryAdjustmentValue value = scholarship.getValue();
        String valueType = value instanceof MonetaryAdjustmentValue.FixedAmount ? "FIXED" : "PERCENTAGE";
        BigDecimal valueAmount = value instanceof MonetaryAdjustmentValue.FixedAmount fixed
                ? fixed.amount()
                : ((MonetaryAdjustmentValue.PercentageAmount) value).percentage();
        int remaining = (int) scholarship.getInstallments().stream()
                .filter(installment -> installment.getStatus() == InstallmentStatus.PENDING)
                .count();
        return new ScholarshipResponse(
                scholarship.getId(), valueType, valueAmount, scholarship.getReason(), scholarship.getStatus().name(),
                scholarship.getInstallments().size(), remaining,
                scholarship.getGrantedBy().getName() + " " + scholarship.getGrantedBy().getLastname(),
                scholarship.getGrantedAt());
    }
}
