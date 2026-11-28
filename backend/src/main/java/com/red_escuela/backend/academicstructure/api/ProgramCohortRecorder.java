package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.repository.ProgramCohortRepository;
import org.springframework.stereotype.Service;

@Service
public class ProgramCohortRecorder {

    private final ProgramCohortRepository programCohortRepository;
    private final CurrentCurriculumPlanProvider currentCurriculumPlanProvider;

    public ProgramCohortRecorder(ProgramCohortRepository programCohortRepository,
                                 CurrentCurriculumPlanProvider currentCurriculumPlanProvider) {
        this.programCohortRepository = programCohortRepository;
        this.currentCurriculumPlanProvider = currentCurriculumPlanProvider;
    }

    public ProgramCohort save(ProgramCohort cohort) {
        if (cohort.getPlan() == null) {
            cohort.setPlan(currentCurriculumPlanProvider.currentPlanOfProgramId(cohort.getProgramId()));
        }
        return programCohortRepository.save(cohort);
    }
}
