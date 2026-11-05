package com.red_escuela.backend.billing.run.dto;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceSimulationAlert;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceSimulationAlertType;

public record MonthlyInvoiceSimulationAlertResponse(Long id, MonthlyInvoiceSimulationAlertType alertType, MonthlyInvoiceEnrollmentType enrollmentType, Long offeringId,
                                                    int affectedEnrollments, String message) {

    public static MonthlyInvoiceSimulationAlertResponse of(MonthlyInvoiceSimulationAlert alert) {
        return new MonthlyInvoiceSimulationAlertResponse(alert.getId(), alert.getAlertType(), alert.getEnrollmentType(), alert.getOfferingId(), alert.getAffectedEnrollments(),
                alert.getMessage());
    }
}
