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

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.LATE_PAYMENT;
    }
}
