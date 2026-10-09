package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.CohortEntryDiscount;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.EffectiveConfigurations;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

@UseCase
public class FindProgramBillingConfigurationService implements FindProgramBillingConfigurationUseCase {
    private final ConfigurationVersionRepository repository;
    private final Clock clock;

    public FindProgramBillingConfigurationService(ConfigurationVersionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public YearBillingTerms findYearTermsInEffect(Long programId, Long termId) {
        LocalDate today = LocalDate.now(clock);
        ConfigurationTopic topic = ConfigurationTopic.ofYearTerms(programId, termId);
        return EffectiveConfigurations.requireInEffect(repository.findEffectiveVersion(topic.scope().name(), topic.key(), today)
                .map(version -> (YearBillingTerms) version.values()), topic, today);
    }

    @Override
    public Percentage findEntryDiscountInEffect(Long cohortId) {
        ConfigurationTopic topic = ConfigurationTopic.ofCohortEntry(cohortId);
        Optional<CohortEntryDiscount> discount = repository.findEffectiveVersion(topic.scope().name(), topic.key(), LocalDate.now(clock))
                .map(version -> (CohortEntryDiscount) version.values());
        return discount.map(CohortEntryDiscount::discount).orElse(Percentage.ofFraction(BigDecimal.ZERO));
    }
}
