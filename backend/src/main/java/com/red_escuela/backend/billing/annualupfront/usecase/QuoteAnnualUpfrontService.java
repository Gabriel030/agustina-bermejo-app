package com.red_escuela.backend.billing.annualupfront.usecase;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontEligibility;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontQuoteService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class QuoteAnnualUpfrontService implements QuoteAnnualUpfrontUseCase {
    private final AnnualUpfrontQuoteService quoteService;

    public QuoteAnnualUpfrontService(AnnualUpfrontQuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    @Transactional(readOnly = true)
    public AnnualUpfrontEligibility quoteAnnualUpfrontForCurrentStudent(Long programEnrollmentId) {
        return quoteService.quoteForCurrentStudent(programEnrollmentId);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @Transactional(readOnly = true)
    public AnnualUpfrontEligibility quoteAnnualUpfrontForStaff(Long programEnrollmentId) {
        return quoteService.quoteForStaff(programEnrollmentId);
    }
}
