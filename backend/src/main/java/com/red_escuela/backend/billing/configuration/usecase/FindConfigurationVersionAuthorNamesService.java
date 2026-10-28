package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.service.ConfigurationVersionAuthorNames;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Collection;
import java.util.Map;

@UseCase
public class FindConfigurationVersionAuthorNamesService implements FindConfigurationVersionAuthorNamesUseCase {
    private final ConfigurationVersionAuthorNames authorNames;

    public FindConfigurationVersionAuthorNamesService(ConfigurationVersionAuthorNames authorNames) {
        this.authorNames = authorNames;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public Map<Long, String> findAuthorNamesOf(Collection<ConfigurationVersion> versions) {
        return authorNames.findNamesOf(versions);
    }
}
