package com.red_escuela.backend.billing.configuration.domain;

import java.util.List;

public record IssuanceSettings(IssuanceMode issuanceMode, Boolean continueIssuingWhileOverdue, int maxRetryAttempts, List<Integer> retryBackoffHours)
        implements ConfigurationValues {

    public IssuanceSettings {
        if (issuanceMode == null) {
            throw new InvalidConfigurationValuesException("El modo de emisión es obligatorio");
        }
        if (continueIssuingWhileOverdue == null) {
            throw new InvalidConfigurationValuesException("Seguir emitiendo con deuda vencida debe definirse explícitamente");
        }
        if (retryBackoffHours == null) {
            throw new InvalidConfigurationValuesException("Las esperas entre reintentos son obligatorias");
        }
        retryBackoffHours = List.copyOf(retryBackoffHours);
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.ISSUANCE;
    }
}
