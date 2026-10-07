package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "INVALID_LATE_PAYMENT_SETTINGS", decisions = {"D-061", "D-147", "D-149"})
public final class LateFeeSettingsCoherencePolicy implements Policy<LatePaymentTerms> {
    private static final String CODE = "INVALID_LATE_PAYMENT_SETTINGS";

    public String ruleName() {
        return "LateFeeSettingsCoherencePolicy";
    }

    public PolicyVerdict evaluateRule(LatePaymentTerms terms) {
        if (terms.gracePeriodDays() < 0) {
            return deny("Los días de gracia no pueden ser negativos.");
        }
        if (terms.suspensionAfterDays() < 0) {
            return deny("Los días para suspender no pueden ser negativos.");
        }
        if (terms.gracePeriodDays() > terms.suspensionAfterDays()) {
            return deny("Los días de gracia no pueden superar los días para suspender.");
        }
        if (terms.suspensionAfterInvoiceCount() < 1) {
            return deny("La cantidad de facturas vencidas para suspender debe ser al menos 1.");
        }
        if (terms.lateFeeCapMonths() != null && terms.lateFeeCapMonths() < 1) {
            return deny("El tope del recargo debe ser de al menos 1 mes.");
        }
        if (terms.lateFeeCapPercentage() != null && terms.lateFeeCapPercentage().fraction().signum() <= 0) {
            return deny("El tope del recargo en porcentaje debe ser mayor que cero.");
        }
        if (terms.lateFeeDebitNoteDueDays() < 0) {
            return deny("Los días de vencimiento de la nota de débito no pueden ser negativos.");
        }
        return PolicyVerdict.allow(ruleName());
    }

    private PolicyVerdict deny(String message) {
        return PolicyVerdict.deny(ruleName(), CODE, message);
    }
}
