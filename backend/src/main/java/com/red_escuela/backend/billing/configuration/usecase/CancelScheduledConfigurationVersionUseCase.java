package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;

public interface CancelScheduledConfigurationVersionUseCase {
    ConfigurationVersion cancelScheduledVersion(Long configurationVersionId, String reason);
}
