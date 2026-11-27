package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.academicstructure.api.ProgramCohortRecorder;
import com.red_escuela.backend.academicstructure.dto.CreateProgramTermCohortRequest;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.exception.ProgramCohortAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.ProgramCohortBillingTemplateRequiredException;
import com.red_escuela.backend.academicstructure.repository.ProgramCohortRepository;
import com.red_escuela.backend.identity.specialist.CurrentPersonService;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramTermCohortCreation {

    private final ProgramCohortRepository cohortRepository;
    private final ProgramCohortRecorder cohortRecorder;
    private final AcademicReferenceFinder referenceFinder;
    private final CurrentPersonService currentPersonService;
    private final CurrentBillingModePort billingMode;

    public ProgramTermCohortCreation(ProgramCohortRepository cohortRepository, ProgramCohortRecorder cohortRecorder,
                                     AcademicReferenceFinder referenceFinder,
                                     CurrentPersonService currentPersonService, CurrentBillingModePort billingMode) {
        this.cohortRepository = cohortRepository;
        this.cohortRecorder = cohortRecorder;
        this.referenceFinder = referenceFinder;
        this.currentPersonService = currentPersonService;
        this.billingMode = billingMode;
    }

    @Transactional
    public ProgramCohort create(Long programId, Long termId, CreateProgramTermCohortRequest request) {
        if (billingMode.currentMode() != BillingMode.OFF) {
            throw new ProgramCohortBillingTemplateRequiredException();
        }
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
        return cohortRecorder.save(cohort);
    }
}
