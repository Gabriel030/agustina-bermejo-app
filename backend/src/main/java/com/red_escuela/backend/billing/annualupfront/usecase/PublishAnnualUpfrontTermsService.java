package com.red_escuela.backend.billing.annualupfront.usecase;

import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontTermsPublisher;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontTermsView;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class PublishAnnualUpfrontTermsService implements PublishAnnualUpfrontTermsUseCase {
    private final AnnualUpfrontTermsPublisher publisher;

    public PublishAnnualUpfrontTermsService(AnnualUpfrontTermsPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    @PreAuthorize("hasRole('DIRECTOR')")
    public AnnualUpfrontTermsView publishNewAnnualUpfrontTermsVersion(String text) {
        return publisher.publishNewTermsVersion(text);
    }
}
