package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.academicstructure.dto.CreateProgramTermCohortRequest;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.exception.ProgramCohortAlreadyExistsException;
import com.red_escuela.backend.academicstructure.repository.ProgramCohortRepository;
import com.red_escuela.backend.identity.specialist.CurrentPersonService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramTermCohortCreation {

    private final ProgramCohortRepository cohortRepository;
    private final AcademicReferenceFinder referenceFinder;
    private final CurrentPersonService currentPersonService;

    public ProgramTermCohortCreation(ProgramCohortRepository cohortRepository, AcademicReferenceFinder referenceFinder,
                                     CurrentPersonService currentPersonService) {
        this.cohortRepository = cohortRepository;
        this.referenceFinder = referenceFinder;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public ProgramCohort create(Long programId, Long termId, CreateProgramTermCohortRequest request) {
        referenceFinder.findProgram(programId);
        referenceFinder.findTerm(termId);
        if (cohortRepository.findByProgramIdAndTermIdAndIntake(programId, termId, request.intake()).isPresent()) {
            throw new ProgramCohortAlreadyExistsException(programId, termId, request.intake());
        }
        ProgramCohort cohort = new ProgramCohort();
        cohort.setProgramId(programId);
        cohort.setTermId(termId);
        cohort.setIntake(request.intake());
        cohort.setAdmissionStart(request.admissionStart());
        cohort.setAdmissionEnd(request.admissionEnd());
        cohort.setCursadaStart(request.cursadaStart());
        cohort.setCreatedBy(currentPersonService.resolveCurrentPerson());
        return cohortRepository.save(cohort);
    }
}
