package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationSchemeLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class SectionEvaluationSchemeFinder {

    private final SectionFinder sectionFinder;
    private final SectionEvaluationSchemeLoader schemeLoader;

    public SectionEvaluationSchemeFinder(SectionFinder sectionFinder, SectionEvaluationSchemeLoader schemeLoader) {
        this.sectionFinder = sectionFinder;
        this.schemeLoader = schemeLoader;
    }

    public Optional<EvaluationSchemeSummary> findProgramSectionScheme(Long sectionId) {
        if (!sectionFinder.exists(SectionKind.PROGRAM, sectionId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(schemeLoader.ofProgramSection(sectionFinder.findProgramSectionEntity(sectionId)))
                .map(this::summarize);
    }

    public Optional<EvaluationSchemeSummary> findCourseSectionScheme(Long sectionId) {
        if (!sectionFinder.exists(SectionKind.COURSE, sectionId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(schemeLoader.ofCourseSection(sectionFinder.findCourseSectionEntity(sectionId)))
                .map(this::summarize);
    }

    private EvaluationSchemeSummary summarize(EvaluationScheme scheme) {
        return new EvaluationSchemeSummary(
                scheme.getExamCountExpected(),
                scheme.getTpCountExpected(),
                scheme.getHasFinal(),
                scheme.getMinApprovalGrade() == null ? null : scheme.getMinApprovalGrade().value().doubleValue());
    }
}
