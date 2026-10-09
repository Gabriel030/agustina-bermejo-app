package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Component
public class DirectConfigurationPublisher {
    private final ConfigurationVersionRepository repository;
    private final ConfigurationVersionActivator activator;
    private final Clock clock;

    public DirectConfigurationPublisher(ConfigurationVersionRepository repository, ConfigurationVersionActivator activator, Clock clock) {
        this.repository = repository;
        this.activator = activator;
        this.clock = clock;
    }

    public ConfigurationVersion publishVersion(ConfigurationTopic topic, ConfigurationValues values, LocalDate effectiveFrom, String changeReason, Long publisherId) {
        LocalDate today = LocalDate.now(clock);
        Long previousId = repository.findEffectiveVersion(topic.scope().name(), topic.key(), today).map(ConfigurationVersion::getId).orElse(null);
        ConfigurationVersion published = repository.saveAndFlush(ConfigurationVersion.publish(topic, values, effectiveFrom, changeReason, publisherId, today, previousId, Instant.now(clock)));
        if (published.getStatus() == ConfigurationVersionStatus.EFFECTIVE) {
            activator.supersedeOlderEffectiveVersions(published);
        }
        return published;
    }
}
