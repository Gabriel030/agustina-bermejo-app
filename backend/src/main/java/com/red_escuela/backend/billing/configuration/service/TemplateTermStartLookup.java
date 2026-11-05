package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.shared.billingterms.ProgramTermStartLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class TemplateTermStartLookup implements ProgramTermStartLookup {
    private final ProgramYearBillingTemplateRepository templateRepository;

    public TemplateTermStartLookup(ProgramYearBillingTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> findTermStartOfTemplate(Long templateId) {
        return templateRepository.findById(templateId).map(ProgramYearBillingTemplate::termStartDate).map(start -> start.toLocalDate());
    }
}
