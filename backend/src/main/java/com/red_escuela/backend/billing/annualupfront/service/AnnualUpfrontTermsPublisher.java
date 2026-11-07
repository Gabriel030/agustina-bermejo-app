package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontTermsVersion;
import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontTermsVersionRepository;
import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AnnualUpfrontTermsPublisher {
    private final AnnualUpfrontTermsVersionRepository repository;
    private final BillingActorResolver actorResolver;
    private final Clock clock;

    public AnnualUpfrontTermsPublisher(AnnualUpfrontTermsVersionRepository repository, BillingActorResolver actorResolver, Clock clock) {
        this.repository = repository;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    @Transactional
    public AnnualUpfrontTermsView publishNewTermsVersion(String text) {
        int nextNumber = repository.findLatestForUpdate().map(latest -> latest.getVersionNumber() + 1).orElse(1);
        AnnualUpfrontTermsVersion saved = repository.saveAndFlush(AnnualUpfrontTermsVersion.publishVersion(nextNumber, text, actorResolver.currentPersonId(), LocalDateTime.now(clock)));
        return AnnualUpfrontTermsView.of(saved, true);
    }
}
