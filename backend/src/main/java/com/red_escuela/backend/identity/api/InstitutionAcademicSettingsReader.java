package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.repository.InstitutionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InstitutionAcademicSettingsReader {

    private final InstitutionRepository institutionRepository;

    public InstitutionAcademicSettingsReader(InstitutionRepository institutionRepository) {
        this.institutionRepository = institutionRepository;
    }

    @Transactional(readOnly = true)
    public InstitutionAcademicSettings read() {
        return institutionRepository.findAll().stream().findFirst()
                .map(institution -> new InstitutionAcademicSettings(
                        institution.getJustificationDeadlineBusinessDays(), institution.getEvaluationConfigDueDays()))
                .orElse(InstitutionAcademicSettings.DEFAULTS);
    }
}
