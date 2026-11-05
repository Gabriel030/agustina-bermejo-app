package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;

import java.util.Collection;
import java.util.Map;

public interface FindConfigurationVersionAuthorNamesUseCase {
    Map<Long, String> findAuthorNamesOf(Collection<ConfigurationVersion> versions);
}
