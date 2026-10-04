package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class UpdateCohortEnrollmentFeeMonthService implements UpdateCohortEnrollmentFeeMonthUseCase {
    private final ProgramCohortRepository programCohortRepository;

    public UpdateCohortEnrollmentFeeMonthService(ProgramCohortRepository programCohortRepository) {
        this.programCohortRepository = programCohortRepository;
    }

    @Override
    @Transactional
    public ProgramCohort update(Long cohortId, Integer enrollmentFeeMonth) {
        ProgramCohort cohort = programCohortRepository.findById(cohortId).orElseThrow(() -> new ProgramCohortNotFoundException(cohortId));
        cohort.setEnrollmentFeeMonth(enrollmentFeeMonth);
        return programCohortRepository.save(cohort);
    }
}
