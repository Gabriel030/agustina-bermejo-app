package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.billing.configuration.service.LegacyConfigurationMirror;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.transaction.annotation.Transactional;


@UseCase
public class UpdateCohortEntryFeeDiscountService implements UpdateCohortEntryFeeDiscountUseCase {
    private final ProgramCohortRepository programCohortRepository;
    private final LegacyConfigurationMirror configurationMirror;

    public UpdateCohortEntryFeeDiscountService(ProgramCohortRepository programCohortRepository, LegacyConfigurationMirror configurationMirror) {
        this.configurationMirror = configurationMirror;
        this.programCohortRepository = programCohortRepository;
    }

    @Override
    @Transactional
    public ProgramCohort update(Long cohortId, Percentage discount) {
        ProgramCohort cohort = programCohortRepository.findById(cohortId).orElseThrow(() -> new ProgramCohortNotFoundException(cohortId));
        cohort.setEntryFeeDiscount(discount);
        ProgramCohort saved = programCohortRepository.save(cohort);
        configurationMirror.recordCohortEntryDiscount(saved);
        return saved;
    }
}
