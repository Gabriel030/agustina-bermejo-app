package com.red_escuela.backend.billing.configuration.domain;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.red_escuela.backend.shared.domain.Percentage;

import java.util.Map;

public record LatePaymentTerms(int gracePeriodDays, Percentage monthlyLateFeeRate, int suspensionAfterDays, int suspensionAfterInvoiceCount,
                               boolean blockNewEnrollmentsWhenOverdue, int lateFeeDebitNoteDueDays, boolean freezeLateFeesOnWithdrawal,
                               boolean lateFeeChargingEnabled) implements ConfigurationValues {

    public LatePaymentTerms {
        if (monthlyLateFeeRate == null) {
            throw new InvalidConfigurationValuesException("La tasa mensual de recargo es obligatoria");
        }
    }

    @JsonCreator
    static LatePaymentTerms fromJson(@JsonProperty("gracePeriodDays") int gracePeriodDays, @JsonProperty("monthlyLateFeeRate") Percentage monthlyLateFeeRate,
                                     @JsonProperty("suspensionAfterDays") int suspensionAfterDays, @JsonProperty("suspensionAfterInvoiceCount") int suspensionAfterInvoiceCount,
                                     @JsonProperty("blockNewEnrollmentsWhenOverdue") boolean blockNewEnrollmentsWhenOverdue,
                                     @JsonProperty("lateFeeDebitNoteDueDays") int lateFeeDebitNoteDueDays,
                                     @JsonProperty("freezeLateFeesOnWithdrawal") boolean freezeLateFeesOnWithdrawal,
                                     @JsonProperty("lateFeeChargingEnabled") boolean lateFeeChargingEnabled, @JsonAnySetter Map<String, Object> unknownProperties) {
        if (!unknownProperties.isEmpty()) {
            throw new InvalidConfigurationValuesException("La mora no admite estos campos: " + unknownProperties.keySet());
        }
        return new LatePaymentTerms(gracePeriodDays, monthlyLateFeeRate, suspensionAfterDays, suspensionAfterInvoiceCount, blockNewEnrollmentsWhenOverdue,
                lateFeeDebitNoteDueDays, freezeLateFeesOnWithdrawal, lateFeeChargingEnabled);
    }

    public static LatePaymentTerms initial() {
        return new LatePaymentTerms(0, Percentage.ofFraction(java.math.BigDecimal.ZERO), 0, 1, true, 10, true, false);
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.LATE_PAYMENT;
    }
}
