package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.billing.configuration.service.LegacyConfigurationMirror;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.transaction.annotation.Transactional;


@UseCase
public class UpdateCohortEntryFeeDiscountService implements UpdateCohortEntryFeeDiscountUseCase {
    private final ProgramCohortRepository programCohortRepository;
    private final LegacyConfigurationMirror configurationMirror;
    private final CurrentPersonService currentPersonService;

    public UpdateCohortEntryFeeDiscountService(ProgramCohortRepository programCohortRepository, LegacyConfigurationMirror configurationMirror,
                                                CurrentPersonService currentPersonService) {
        this.configurationMirror = configurationMirror;
        this.currentPersonService = currentPersonService;
        this.programCohortRepository = programCohortRepository;
    }

    @Override
    @Transactional
    public ProgramCohort update(Long cohortId, Percentage discount) {
        ProgramCohort cohort = programCohortRepository.findById(cohortId).orElseThrow(() -> new ProgramCohortNotFoundException(cohortId));
        cohort.setEntryFeeDiscount(discount);
        ProgramCohort saved = programCohortRepository.save(cohort);
        configurationMirror.recordCohortEntryDiscount(saved, currentPersonService.resolveCurrentPerson().getId());
        return saved;
    }
}
