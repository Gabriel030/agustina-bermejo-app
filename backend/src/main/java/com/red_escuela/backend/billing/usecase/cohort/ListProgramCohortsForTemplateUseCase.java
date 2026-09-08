package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.entity.ProgramCohort;

import java.util.List;

public interface ListProgramCohortsForTemplateUseCase {
    List<ProgramCohort> list(Long templateId);
}
