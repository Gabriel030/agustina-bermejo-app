package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListConfigurationVersionHistoryService implements ListConfigurationVersionHistoryUseCase {
    private final ConfigurationVersionRepository repository;

    public ListConfigurationVersionHistoryService(ConfigurationVersionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<ConfigurationVersion> listVersionHistory(ConfigurationTopic topic) {
        return repository.findByScopeAndTopicKeyOrderByEffectiveFromDescIdDesc(topic.scope(), topic.key());
    }
}
