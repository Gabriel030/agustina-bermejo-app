package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class SectionEvaluationSchemeFinder {

    private final SectionFinder sectionFinder;

    public SectionEvaluationSchemeFinder(SectionFinder sectionFinder) {
        this.sectionFinder = sectionFinder;
    }

    public Optional<EvaluationSchemeSummary> findProgramSectionScheme(Long sectionId) {
        if (!sectionFinder.exists(SectionKind.PROGRAM, sectionId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(sectionFinder.findProgramSectionEntity(sectionId).getEvaluationScheme())
                .map(this::summarize);
    }

    public Optional<EvaluationSchemeSummary> findCourseSectionScheme(Long sectionId) {
        if (!sectionFinder.exists(SectionKind.COURSE, sectionId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(sectionFinder.findCourseSectionEntity(sectionId).getEvaluationScheme())
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
