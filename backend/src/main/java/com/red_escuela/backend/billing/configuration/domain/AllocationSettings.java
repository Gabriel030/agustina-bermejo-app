package com.red_escuela.backend.billing.configuration.domain;

public record AllocationSettings(boolean autoApplyCreditBalance) implements ConfigurationValues {

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.ALLOCATION;
    }
}
