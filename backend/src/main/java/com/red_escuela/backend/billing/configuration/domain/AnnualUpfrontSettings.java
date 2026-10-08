package com.red_escuela.backend.billing.configuration.domain;

import java.time.LocalDate;

public record AnnualUpfrontSettings(LocalDate requestDeadline, int paymentDeadlineHours) implements ConfigurationValues {

    public AnnualUpfrontSettings {
        if (requestDeadline == null) {
            throw new InvalidConfigurationValuesException("La fecha límite del pago anual es obligatoria");
        }
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.ANNUAL_UPFRONT;
    }
}
