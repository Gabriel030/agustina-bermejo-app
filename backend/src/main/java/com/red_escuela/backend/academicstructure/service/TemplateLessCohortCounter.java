package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.repository.ProgramCohortRepository;
import com.red_escuela.backend.shared.domain.TemplateLessCohortPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateLessCohortCounter implements TemplateLessCohortPort {

    private final ProgramCohortRepository programCohortRepository;

    public TemplateLessCohortCounter(ProgramCohortRepository programCohortRepository) {
        this.programCohortRepository = programCohortRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public long countCohortsWithoutBillingTemplate() {
        return programCohortRepository.countByTemplateIdIsNull();
    }
}
