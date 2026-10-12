package com.red_escuela.backend.billing.usecase.latepaymentsettings;

import java.time.Clock;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.exception.NoActiveLatePaymentSettingsException;
import com.red_escuela.backend.shared.UseCase;

import java.time.LocalDate;

@UseCase
public class FindCurrentLatePaymentSettingsService implements FindCurrentLatePaymentSettingsUseCase {

    private final ConfigurationVersionRepository repository;
    private final Clock clock;

    public FindCurrentLatePaymentSettingsService(ConfigurationVersionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public ConfigurationVersion findCurrent() {
        ConfigurationTopic topic = ConfigurationTopic.ofGlobal(ConfigurationScope.LATE_PAYMENT);
        return repository.findEffectiveVersion(topic.scope().name(), topic.key(), LocalDate.now(clock))
                .orElseThrow(NoActiveLatePaymentSettingsException::new);
    }
}
