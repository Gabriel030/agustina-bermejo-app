package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.SubjectSection;

public interface SectionEvaluationSetupPort {
    void createInitialEvaluationDraft(SubjectSection section);

    void requireEvaluationSchemeExists(Long evaluationSchemeId);
}
