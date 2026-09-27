package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListProgramBillingTemplatesService implements ListProgramBillingTemplatesUseCase {

    private final ProgramYearBillingTemplateRepository templateRepository;

    public ListProgramBillingTemplatesService(ProgramYearBillingTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override
    public List<ProgramYearBillingTemplate> list(Long programId) {
        return templateRepository.findAllByProgramId(programId);
    }
}
