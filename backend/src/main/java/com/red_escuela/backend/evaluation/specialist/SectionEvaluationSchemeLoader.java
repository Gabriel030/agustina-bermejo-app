package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.repository.EvaluationSchemeRepository;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

@Component
public class SectionEvaluationSchemeLoader {

    private final EvaluationSchemeRepository schemeRepository;

    public SectionEvaluationSchemeLoader(EvaluationSchemeRepository schemeRepository) {
        this.schemeRepository = schemeRepository;
    }

    public EvaluationScheme ofProgramSection(SubjectSection section) {
        return section == null ? null : resolve(section.getEvaluationScheme());
    }

    public EvaluationScheme ofCourseSection(CourseSubjectSection section) {
        return section == null ? null : resolve(section.getEvaluationScheme());
    }

    public EvaluationScheme ofConfig(SectionEvaluationConfig config) {
        return config.isOwnedByCourseSection()
                ? ofCourseSection(config.getCourseSection())
                : ofProgramSection(config.getSubjectSection());
    }

    private EvaluationScheme resolve(EvaluationScheme related) {
        if (related == null || Hibernate.isInitialized(related)) {
            return related;
        }
        return related.getId() == null ? null : schemeRepository.findById(related.getId()).orElse(null);
    }
}
