package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.repository.ProgramCohortRepository;
import org.springframework.stereotype.Service;

@Service
public class ProgramCohortRecorder {

    private final ProgramCohortRepository programCohortRepository;

    public ProgramCohortRecorder(ProgramCohortRepository programCohortRepository) {
        this.programCohortRepository = programCohortRepository;
    }

    public ProgramCohort save(ProgramCohort cohort) {
        return programCohortRepository.save(cohort);
    }
}
