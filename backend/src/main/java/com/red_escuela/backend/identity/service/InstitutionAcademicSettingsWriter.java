package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.InstitutionAcademicSettings;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.exception.InstitutionNotFoundException;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstitutionAcademicSettingsWriter {

    private final InstitutionRepository institutionRepository;

    public InstitutionAcademicSettingsWriter(InstitutionRepository institutionRepository) {
        this.institutionRepository = institutionRepository;
    }

    @Transactional
    public InstitutionAcademicSettings write(int justificationDeadlineBusinessDays, int evaluationConfigDueDays) {
        Institution institution = institutionRepository.findAll().stream().findFirst()
                .orElseThrow(InstitutionNotFoundException::new);
        institution.setJustificationDeadlineBusinessDays(justificationDeadlineBusinessDays);
        institution.setEvaluationConfigDueDays(evaluationConfigDueDays);
        institutionRepository.save(institution);
        return new InstitutionAcademicSettings(justificationDeadlineBusinessDays, evaluationConfigDueDays);
    }
}
