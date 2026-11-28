package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.ProgramCohortNotFoundException;
import com.red_escuela.backend.academicstructure.dto.CohortReadinessResponse;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.exception.ProgramCohortAlreadyOpenException;
import com.red_escuela.backend.academicstructure.exception.ProgramCohortNotReadyException;
import com.red_escuela.backend.academicstructure.repository.ProgramCohortRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ProgramCohortOpening {

    private final ProgramCohortRepository cohortRepository;
    private final CohortReadinessChecker readinessChecker;
    private final Clock clock;

    public ProgramCohortOpening(ProgramCohortRepository cohortRepository, CohortReadinessChecker readinessChecker, Clock clock) {
        this.cohortRepository = cohortRepository;
        this.readinessChecker = readinessChecker;
        this.clock = clock;
    }

    @Transactional
    public ProgramCohort open(Long cohortId) {
        ProgramCohort cohort = cohortRepository.findById(cohortId).orElseThrow(() -> new ProgramCohortNotFoundException(cohortId));
        if (cohort.isOpen()) {
            throw new ProgramCohortAlreadyOpenException(cohortId);
        }
        CohortReadinessResponse readiness = readinessChecker.check(cohortId);
        if (!readiness.ready()) {
            throw new ProgramCohortNotReadyException(cohortId, readiness.missingBlockingCodes());
        }
        cohort.markOpened(LocalDateTime.now(clock));
        return cohortRepository.save(cohort);
    }
}
