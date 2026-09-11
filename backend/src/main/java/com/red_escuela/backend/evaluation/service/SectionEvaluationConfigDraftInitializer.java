package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectionEvaluationConfigDraftInitializer {

    private final SectionEvaluationConfigRepository configRepository;

    public SectionEvaluationConfigDraftInitializer(SectionEvaluationConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Transactional
    public SectionEvaluationConfig createInitialDraft(SubjectSection section) {
        SectionEvaluationConfig config = new SectionEvaluationConfig();
        config.setSubjectSection(section);
        config.setStatus(SectionEvaluationConfigStatus.DRAFT);
        return configRepository.save(config);
    }
}
