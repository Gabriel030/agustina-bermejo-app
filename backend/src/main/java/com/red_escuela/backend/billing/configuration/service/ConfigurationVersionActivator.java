package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
public class ConfigurationVersionActivator {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationVersionActivator.class);
    private static final Comparator<ConfigurationVersion> RESOLUTION_ORDER = Comparator
            .comparing(ConfigurationVersion::getEffectiveFrom)
            .thenComparing(ConfigurationVersion::getDecidedAt)
            .thenComparing(ConfigurationVersion::getId);

    private final ConfigurationVersionRepository repository;
    private final Clock clock;

    public ConfigurationVersionActivator(ConfigurationVersionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void supersedeOlderEffectiveVersions(ConfigurationVersion newest) {
        Instant now = Instant.now(clock);
        List<ConfigurationVersion> effective = repository.findByScopeAndTopicKeyAndStatus(newest.getScope(), newest.getTopicKey(), ConfigurationVersionStatus.EFFECTIVE);
        effective.stream()
                .filter(other -> !other.getId().equals(newest.getId()) && RESOLUTION_ORDER.compare(other, newest) < 0)
                .forEach(other -> other.supersede(now));
    }

    @Transactional
    public int activateDueVersions() {
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        List<ConfigurationVersion> due = repository.findByStatusAndEffectiveFromLessThanEqual(ConfigurationVersionStatus.SCHEDULED, today).stream()
                .sorted(RESOLUTION_ORDER)
                .toList();
        for (ConfigurationVersion version : due) {
            version.reachStart(now);
            repository.saveAndFlush(version);
            supersedeOlderEffectiveVersions(version);
            log.info("Configuration version effective: scope={}, topic={}, version={}", version.getScope(), version.getTopicKey(), version.getId());
        }
        return due.size();
    }
}
