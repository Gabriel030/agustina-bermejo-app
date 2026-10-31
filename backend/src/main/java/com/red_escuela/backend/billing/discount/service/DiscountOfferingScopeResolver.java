package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.academicstructure.api.OfferingCurrencyFinder;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.exception.DiscountScopeOfferingNotFoundException;
import com.red_escuela.backend.billing.discount.policy.DiscountDefinitionCheck;
import com.red_escuela.backend.billing.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.shared.domain.Currency;
import org.springframework.stereotype.Component;

@Component
public class DiscountOfferingScopeResolver {
    private final OfferingCurrencyFinder currencyFinder;
    private final ProgramCohortRepository programCohortRepository;

    public DiscountOfferingScopeResolver(OfferingCurrencyFinder currencyFinder, ProgramCohortRepository programCohortRepository) {
        this.currencyFinder = currencyFinder;
        this.programCohortRepository = programCohortRepository;
    }

    public DiscountDefinitionCheck resolveScopeOf(DiscountDefinitionDraft draft) {
        Currency offeringCurrency = findOfferingCurrency(draft);
        Long cohortProgramId = draft.programCohortId() == null ? null : programCohortRepository.findById(draft.programCohortId())
                .orElseThrow(() -> new ProgramCohortNotFoundException(draft.programCohortId())).getTemplate().programId();
        return new DiscountDefinitionCheck(draft, offeringCurrency, cohortProgramId);
    }

    private Currency findOfferingCurrency(DiscountDefinitionDraft draft) {
        if (draft.programId() != null) {
            return currencyFinder.findProgramCurrency(draft.programId()).orElseThrow(() -> new DiscountScopeOfferingNotFoundException("la carrera", draft.programId()));
        }
        if (draft.courseId() != null) {
            return currencyFinder.findCourseCurrency(draft.courseId()).orElseThrow(() -> new DiscountScopeOfferingNotFoundException("el curso", draft.courseId()));
        }
        return null;
    }
}
