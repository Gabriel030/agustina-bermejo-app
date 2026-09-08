package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import org.springframework.stereotype.Service;

@Service
public class ProgramYearBillingTemplateService {

    private final ProgramYearBillingTemplateRepository templateRepository;

    public ProgramYearBillingTemplateService(ProgramYearBillingTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    public ProgramYearBillingTemplate findOrThrow(Long id) {
        return templateRepository.findById(id).orElseThrow(() -> new ProgramYearBillingTemplateNotFoundException(id));
    }
}
