package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.ProgramCohort;
import com.red_escuela.backend.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.repository.ProgramCohortRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ProgramCohortService {

    private final ProgramCohortRepository programCohortRepository;
    private final ProgramYearBillingTemplateService templateService;
    private final CurrentPersonService currentPersonService;

    public ProgramCohortService(ProgramCohortRepository programCohortRepository, ProgramYearBillingTemplateService templateService,
                                 CurrentPersonService currentPersonService) {
        this.programCohortRepository = programCohortRepository;
        this.templateService = templateService;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public ProgramCohort crear(Long templateId, CreateProgramCohortRequest request) {
        ProgramYearBillingTemplate template = templateService.findOrThrow(templateId);
        Person actor = currentPersonService.resolveCurrentPerson();

        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplate(template);
        cohort.setIntake(request.intake());
        cohort.setAdmissionStart(request.admissionStart());
        cohort.setAdmissionEnd(request.admissionEnd());
        cohort.setCursadaStart(request.cursadaStart());
        cohort.setCreatedBy(actor);
        return programCohortRepository.save(cohort);
    }

    public List<ProgramCohort> listarPorPlantilla(Long templateId) {
        return programCohortRepository.findByTemplateId(templateId);
    }

    public ProgramCohort findOrThrow(Long id) {
        return programCohortRepository.findById(id).orElseThrow(() -> new ProgramCohortNotFoundException(id));
    }

    public boolean estaDentroDeLaVentanaDeAdmision(ProgramCohort cohort, LocalDate at) {
        return !at.isBefore(cohort.getAdmissionStart()) && !at.isAfter(cohort.getAdmissionEnd());
    }
}
