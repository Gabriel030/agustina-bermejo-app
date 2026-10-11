package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.math.BigDecimal;

@PolicyRule(violationCodes = "INVALID_LATE_PAYMENT_SETTINGS", decisions = {"D-061", "D-147", "D-149", "D-239", "D-330"})
public final class LateFeeSettingsCoherencePolicy implements Policy<LatePaymentTerms> {
    private static final String CODE = "INVALID_LATE_PAYMENT_SETTINGS";
    private static final int MAX_RATE_FRACTION_DECIMALS = 4;
    private static final BigDecimal MIN_DAILY_RATE = new BigDecimal("0.0001");
    private static final BigDecimal MAX_DAILY_RATE = new BigDecimal("0.005");

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
        if (terms.lateFeeDebitNoteDueDays() < 0) {
            return deny("Los días de vencimiento de la nota de débito no pueden ser negativos.");
        }
        if (terms.dailyLateFeeRate().fraction().stripTrailingZeros().scale() > MAX_RATE_FRACTION_DECIMALS) {
            return deny("El recargo diario admite hasta 2 decimales de porcentaje.");
        }
        if (terms.dailyLateFeeRate().fraction().compareTo(MAX_DAILY_RATE) > 0) {
            return deny("El recargo diario tiene que ser de 0,01 % a 0,50 %.");
        }
        if (terms.lateFeeChargingEnabled() && terms.dailyLateFeeRate().fraction().compareTo(MIN_DAILY_RATE) < 0) {
            return deny("El recargo diario tiene que ser de 0,01 % a 0,50 %.");
        }
        return PolicyVerdict.allow(ruleName());
    }

    private PolicyVerdict deny(String message) {
        return PolicyVerdict.deny(ruleName(), CODE, message);
    }
}
