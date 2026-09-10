package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ProgramCohortService {

    private final ProgramCohortRepository programCohortRepository;

    public ProgramCohortService(ProgramCohortRepository programCohortRepository) {
        this.programCohortRepository = programCohortRepository;
    }

    public ProgramCohort findOrThrow(Long id) {
        return programCohortRepository.findById(id).orElseThrow(() -> new ProgramCohortNotFoundException(id));
    }

    public boolean estaDentroDeLaVentanaDeAdmision(ProgramCohort cohort, LocalDate at) {
        return !at.isBefore(cohort.getAdmissionStart()) && !at.isAfter(cohort.getAdmissionEnd());
    }
}
