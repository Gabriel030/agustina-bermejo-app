package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.repository.EvaluationSchemeRepository;
import org.springframework.stereotype.Component;

@Component
public class SectionEvaluationSchemeLoader {

    private final EvaluationSchemeRepository schemeRepository;

    public SectionEvaluationSchemeLoader(EvaluationSchemeRepository schemeRepository) {
        this.schemeRepository = schemeRepository;
    }

    public EvaluationScheme ofProgramSection(SubjectSection section) {
        return section == null ? null : resolve(section.getEvaluationSchemeId());
    }

    public EvaluationScheme ofCourseSection(CourseSubjectSection section) {
        return section == null ? null : resolve(section.getEvaluationSchemeId());
    }

    public EvaluationScheme ofConfig(SectionEvaluationConfig config) {
        return config.isOwnedByCourseSection()
                ? ofCourseSection(config.getCourseSection())
                : ofProgramSection(config.getSubjectSection());
    }

    private EvaluationScheme resolve(Long schemeId) {
        return schemeId == null ? null : schemeRepository.findById(schemeId).orElse(null);
    }
}
