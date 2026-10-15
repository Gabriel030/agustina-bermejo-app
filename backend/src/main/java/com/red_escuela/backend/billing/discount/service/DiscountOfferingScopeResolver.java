package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.policy.DiscountDefinitionCheck;
import com.red_escuela.backend.billing.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.shared.domain.Currency;
import org.springframework.stereotype.Component;

@Component
public class DiscountOfferingScopeResolver {
    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;
    private final ProgramCohortRepository programCohortRepository;

    public DiscountOfferingScopeResolver(ProgramRepository programRepository, CourseRepository courseRepository, ProgramCohortRepository programCohortRepository) {
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
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
            return programRepository.findById(draft.programId()).orElseThrow(() -> new ProgramNotFoundException(draft.programId())).getCurrency();
        }
        if (draft.courseId() != null) {
            return courseRepository.findById(draft.courseId()).orElseThrow(() -> new CourseNotFoundException(draft.courseId())).getCurrency();
        }
        return null;
    }
}
