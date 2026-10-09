package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.shared.domain.Percentage;

public record LatePaymentTerms(int gracePeriodDays, Percentage monthlyLateFeeRate, Integer lateFeeCapMonths, Percentage lateFeeCapPercentage,
                               int suspensionAfterDays, int suspensionAfterInvoiceCount, boolean blockNewEnrollmentsWhenOverdue,
                               int lateFeeDebitNoteDueDays, boolean freezeLateFeesOnWithdrawal) implements ConfigurationValues {

    public LatePaymentTerms {
        if (monthlyLateFeeRate == null) {
            throw new InvalidConfigurationValuesException("La tasa mensual de recargo es obligatoria");
        }
        if ((lateFeeCapMonths == null) == (lateFeeCapPercentage == null)) {
            throw new InvalidConfigurationValuesException("El tope del recargo se define en meses o en porcentaje, uno solo");
        }
    }

    public static LatePaymentTerms initial() {
        return new LatePaymentTerms(0, Percentage.ofFraction(java.math.BigDecimal.ZERO), 12, null, 0, 1, true, 10, true);
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.LATE_PAYMENT;
    }
}
