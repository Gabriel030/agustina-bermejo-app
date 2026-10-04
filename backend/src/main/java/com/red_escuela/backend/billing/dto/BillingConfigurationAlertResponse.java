package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.BillingConfigurationAlert;

import java.time.LocalDateTime;
import java.time.YearMonth;

public record BillingConfigurationAlertResponse(Long id, Long programId, String programName, YearMonth period, Integer affectedEnrollments,
                                                LocalDateTime detectedAt, LocalDateTime lastDetectedAt, String reason) {

    public static BillingConfigurationAlertResponse from(BillingConfigurationAlert alert) {
        return new BillingConfigurationAlertResponse(alert.getId(), alert.getProgram().getId(), alert.getProgram().getName(), alert.getPeriod(),
                alert.getAffectedEnrollments(), alert.getDetectedAt(), alert.getLastDetectedAt(), alert.getReason());
    }
}
