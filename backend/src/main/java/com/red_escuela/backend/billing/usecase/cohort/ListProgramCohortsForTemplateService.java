package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListProgramCohortsForTemplateService implements ListProgramCohortsForTemplateUseCase {

    private final ProgramCohortRepository programCohortRepository;

    public ListProgramCohortsForTemplateService(ProgramCohortRepository programCohortRepository) {
        this.programCohortRepository = programCohortRepository;
    }

    @Override
    public List<ProgramCohort> list(Long templateId) {
        return programCohortRepository.findByTemplateId(templateId);
    }
}
