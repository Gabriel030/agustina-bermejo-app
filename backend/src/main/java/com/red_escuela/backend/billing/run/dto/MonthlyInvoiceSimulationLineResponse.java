package com.red_escuela.backend.billing.run.dto;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceSimulationLine;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public record MonthlyInvoiceSimulationLineResponse(Long id, MonthlyInvoiceEnrollmentType enrollmentType, Long enrollmentId, Long offeringId, MonthlyInvoiceRunItemResult result,
                                                   String decisionCode, String errorCode, String message, int chargeCount, NonNegativeMoney total, LocalDate dueDate) {

    public static MonthlyInvoiceSimulationLineResponse of(MonthlyInvoiceSimulationLine line) {
        return new MonthlyInvoiceSimulationLineResponse(line.getId(), line.getEnrollmentType(), line.getEnrollmentId(), line.getOfferingId(), line.getResult(),
                line.getDecisionCode(), line.getErrorCode(), line.getMessage(), line.getChargeCount(), line.getTotal(), line.getDueDate());
    }
}
