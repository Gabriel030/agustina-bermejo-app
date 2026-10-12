package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.InstallmentStatus;
import com.red_escuela.backend.billing.entity.Scholarship;

import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import java.time.LocalDateTime;

public record ScholarshipResponse(
        Long id, MonetaryAdjustment value, String reason, String status, int totalInstallments,
        int remainingInstallments, String grantedByName, LocalDateTime grantedAt, String appliesTo) {

    public static ScholarshipResponse from(Scholarship scholarship) {
        int remaining = (int) scholarship.getInstallments().stream()
                .filter(installment -> installment.getStatus() == InstallmentStatus.PENDING)
                .count();
        return new ScholarshipResponse(
                scholarship.getId(), scholarship.getValue(), scholarship.getReason(), scholarship.getStatus().name(),
                scholarship.getInstallments().size(), remaining,
                scholarship.getGrantedBy().getName() + " " + scholarship.getGrantedBy().getLastname(),
                scholarship.getGrantedAt(), scholarship.getAppliesTo().name());
    }
}
