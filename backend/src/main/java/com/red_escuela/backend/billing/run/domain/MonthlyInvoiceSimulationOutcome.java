package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record MonthlyInvoiceSimulationOutcome(MonthlyInvoiceRunItemResult result, Long offeringId, String decisionCode, String errorCode, String message, int chargeCount,
                                              NonNegativeMoney total, NonNegativeMoney installmentTotal, LocalDate dueDate) {
    public static final String YEAR_NOT_CONFIGURED_CODE = "YEAR_NOT_CONFIGURED";
    public static final String PRICE_MISSING_ERROR_CODE = "NoEffectivePriceException";

    public static MonthlyInvoiceSimulationOutcome wouldBeIssued(Long offeringId, int chargeCount, NonNegativeMoney total, NonNegativeMoney installmentTotal, LocalDate dueDate) {
        return new MonthlyInvoiceSimulationOutcome(MonthlyInvoiceRunItemResult.ISSUED, offeringId, null, null, null, chargeCount, total, installmentTotal, dueDate);
    }

    public static MonthlyInvoiceSimulationOutcome wouldBeAlreadyIssued(Long offeringId) {
        return new MonthlyInvoiceSimulationOutcome(MonthlyInvoiceRunItemResult.ALREADY_ISSUED, offeringId, null, null, null, 0, null, null, null);
    }

    public static MonthlyInvoiceSimulationOutcome wouldNotBeIssued(Long offeringId, List<String> planningDecisionCodes) {
        MonthlyInvoiceItemOutcome notApplicable = MonthlyInvoiceItemOutcome.notApplicable(planningDecisionCodes);
        return new MonthlyInvoiceSimulationOutcome(MonthlyInvoiceRunItemResult.NOT_APPLICABLE, offeringId, notApplicable.decisionCode(), null, null, 0, null, null, null);
    }

    public static MonthlyInvoiceSimulationOutcome wouldFail(Long offeringId, String errorCode, String message) {
        MonthlyInvoiceItemOutcome failed = MonthlyInvoiceItemOutcome.failed(errorCode, message);
        return new MonthlyInvoiceSimulationOutcome(MonthlyInvoiceRunItemResult.FAILED, offeringId, null, failed.errorCode(), failed.message(), 0, null, null, null);
    }

    public MonthlyInvoiceSimulationOutcome withOfferingId(Long resolvedOfferingId) {
        return new MonthlyInvoiceSimulationOutcome(result, resolvedOfferingId, decisionCode, errorCode, message, chargeCount, total, installmentTotal, dueDate);
    }

    public Optional<MonthlyInvoiceSimulationAlertType> findAlertType() {
        if (result == MonthlyInvoiceRunItemResult.FAILED) {
            return Optional.of(PRICE_MISSING_ERROR_CODE.equals(errorCode) ? MonthlyInvoiceSimulationAlertType.PRICE_MISSING : MonthlyInvoiceSimulationAlertType.EMISSION_ERROR);
        }
        return YEAR_NOT_CONFIGURED_CODE.equals(decisionCode) ? Optional.of(MonthlyInvoiceSimulationAlertType.YEAR_NOT_CONFIGURED) : Optional.empty();
    }
}
