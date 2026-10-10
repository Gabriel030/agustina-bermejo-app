package com.red_escuela.backend.billing.configuration.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.red_escuela.backend.shared.domain.Percentage;

@JsonIgnoreProperties(ignoreUnknown = false)
public record LatePaymentTerms(int gracePeriodDays, Percentage monthlyLateFeeRate, int suspensionAfterDays, int suspensionAfterInvoiceCount,
                               boolean blockNewEnrollmentsWhenOverdue, int lateFeeDebitNoteDueDays, boolean freezeLateFeesOnWithdrawal,
                               boolean lateFeeChargingEnabled) implements ConfigurationValues {

    public LatePaymentTerms {
        if (monthlyLateFeeRate == null) {
            throw new InvalidConfigurationValuesException("La tasa mensual de recargo es obligatoria");
        }
    }

    public static LatePaymentTerms initial() {
        return new LatePaymentTerms(0, Percentage.ofFraction(java.math.BigDecimal.ZERO), 0, 1, true, 10, true, false);
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.LATE_PAYMENT;
    }
}
