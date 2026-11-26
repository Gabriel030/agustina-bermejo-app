package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.repository.EvaluationSchemeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class SectionEvaluationSchemeFinder {

    private final SectionFinder sectionFinder;
    private final EvaluationSchemeRepository schemeRepository;

    public SectionEvaluationSchemeFinder(SectionFinder sectionFinder, EvaluationSchemeRepository schemeRepository) {
        this.sectionFinder = sectionFinder;
        this.schemeRepository = schemeRepository;
    }

    public Optional<EvaluationSchemeSummary> findProgramSectionScheme(Long sectionId) {
        if (!sectionFinder.exists(SectionKind.PROGRAM, sectionId)) {
            return Optional.empty();
        }
        return findScheme(SectionKind.PROGRAM, sectionId);
    }

    public Optional<EvaluationSchemeSummary> findCourseSectionScheme(Long sectionId) {
        if (!sectionFinder.exists(SectionKind.COURSE, sectionId)) {
            return Optional.empty();
        }
        return findScheme(SectionKind.COURSE, sectionId);
    }

    private Optional<EvaluationSchemeSummary> findScheme(SectionKind kind, Long sectionId) {
        return sectionFinder.findEvaluationSchemeId(kind, sectionId)
                .flatMap(schemeRepository::findById)
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
