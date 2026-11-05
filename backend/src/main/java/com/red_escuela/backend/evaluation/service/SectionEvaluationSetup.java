package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.api.SectionEvaluationSetupPort;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.evaluation.exception.EvaluationSchemeNotFoundException;
import com.red_escuela.backend.evaluation.repository.EvaluationSchemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectionEvaluationSetup implements SectionEvaluationSetupPort {

    private final SectionEvaluationConfigDraftInitializer draftInitializer;
    private final EvaluationSchemeRepository evaluationSchemeRepository;

    public SectionEvaluationSetup(SectionEvaluationConfigDraftInitializer draftInitializer,
                                  EvaluationSchemeRepository evaluationSchemeRepository) {
        this.draftInitializer = draftInitializer;
        this.evaluationSchemeRepository = evaluationSchemeRepository;
    }

    @Override
    @Transactional
    public void createInitialEvaluationDraft(SubjectSection section) {
        draftInitializer.createInitialDraft(section);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireEvaluationSchemeExists(Long evaluationSchemeId) {
        if (!evaluationSchemeRepository.existsById(evaluationSchemeId)) {
            throw new EvaluationSchemeNotFoundException(evaluationSchemeId);
        }
    }
}
