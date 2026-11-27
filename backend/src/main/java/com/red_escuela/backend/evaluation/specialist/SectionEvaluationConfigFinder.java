package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.evaluation.domain.SectionRef;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.exception.SectionEvaluationConfigNotFoundException;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import org.springframework.stereotype.Service;

@Service
public class SectionEvaluationConfigFinder {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionFinder sectionFinder;
    private final SectionEvaluationConfigDraftInitializer draftInitializer;

    public SectionEvaluationConfigFinder(SectionEvaluationConfigRepository configRepository,
                                         SectionFinder sectionFinder,
                                         SectionEvaluationConfigDraftInitializer draftInitializer) {
        this.configRepository = configRepository;
        this.sectionFinder = sectionFinder;
        this.draftInitializer = draftInitializer;
    }

    public SectionEvaluationConfig findOrThrow(SectionRef section) {
        if (section.isCourse()) {
            return configRepository.findByCourseSectionId(section.id())
                    .orElseGet(() -> draftInitializer.createInitialDraft(
                            sectionFinder.findCourseSectionWithSchemeAndTerms(section.id())));
        }
        return configRepository.findBySubjectSectionId(section.id())
                .orElseThrow(() -> new SectionEvaluationConfigNotFoundException(section.id()));
    }
}
