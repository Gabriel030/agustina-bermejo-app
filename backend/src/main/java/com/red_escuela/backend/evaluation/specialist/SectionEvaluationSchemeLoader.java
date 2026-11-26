package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.repository.EvaluationSchemeRepository;
import org.springframework.stereotype.Component;

@Component
public class SectionEvaluationSchemeLoader {

    private final EvaluationSchemeRepository schemeRepository;

    public SectionEvaluationSchemeLoader(EvaluationSchemeRepository schemeRepository) {
        this.schemeRepository = schemeRepository;
    }

    public EvaluationScheme load(SubjectSection section) {
        return load(section.getEvaluationSchemeId());
    }

    public EvaluationScheme load(CourseSubjectSection section) {
        return load(section.getEvaluationSchemeId());
    }

    private EvaluationScheme load(Long schemeId) {
        return schemeId == null ? null : schemeRepository.findById(schemeId).orElse(null);
    }
}
