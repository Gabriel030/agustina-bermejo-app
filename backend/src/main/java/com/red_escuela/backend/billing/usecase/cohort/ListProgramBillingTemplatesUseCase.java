package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;

import java.util.List;

public interface ListProgramBillingTemplatesUseCase {
    List<ProgramYearBillingTemplate> list(Long programId);
}
