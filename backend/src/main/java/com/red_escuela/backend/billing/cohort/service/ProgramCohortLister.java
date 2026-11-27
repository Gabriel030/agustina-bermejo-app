package com.red_escuela.backend.billing.cohort.service;

import com.red_escuela.backend.academicstructure.api.ProgramCohortFinder;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProgramCohortLister {

    private final ProgramCohortFinder programCohortFinder;
    private final ProgramYearBillingTemplateRepository templateRepository;

    public ProgramCohortLister(ProgramCohortFinder programCohortFinder, ProgramYearBillingTemplateRepository templateRepository) {
        this.programCohortFinder = programCohortFinder;
        this.templateRepository = templateRepository;
    }

    public List<ProgramCohort> listForTemplate(Long templateId) {
        return programCohortFinder.findByTemplateId(templateId);
    }

    public List<ProgramCohort> listForProgram(Long programId) {
        return programCohortFinder.findByTemplateIds(templateRepository.findAllByProgramId(programId).stream().map(ProgramYearBillingTemplate::getId).toList());
    }
}
