package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionEvaluationSetupPort;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.evaluation.exception.EvaluationSchemeNotFoundException;
import com.red_escuela.backend.evaluation.repository.EvaluationSchemeRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectionEvaluationSetupPortAdapter implements SectionEvaluationSetupPort {

    private final SectionEvaluationConfigDraftInitializer draftInitializer;
    private final EvaluationSchemeRepository evaluationSchemeRepository;
    private final GradeItemRepository gradeItemRepository;

    public SectionEvaluationSetupPortAdapter(SectionEvaluationConfigDraftInitializer draftInitializer,
                                  EvaluationSchemeRepository evaluationSchemeRepository,
                                  GradeItemRepository gradeItemRepository) {
        this.draftInitializer = draftInitializer;
        this.evaluationSchemeRepository = evaluationSchemeRepository;
        this.gradeItemRepository = gradeItemRepository;
    }

    @Override
    @Transactional
    public void createInitialEvaluationDraft(SubjectSection section) {
        draftInitializer.createInitialDraft(section);
    }

    @Override
    @Transactional
    public void createInitialEvaluationDraftForCourseSection(CourseSubjectSection section) {
        draftInitializer.createInitialDraft(section);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireEvaluationSchemeExists(Long evaluationSchemeId) {
        if (!evaluationSchemeRepository.existsById(evaluationSchemeId)) {
            throw new EvaluationSchemeNotFoundException(evaluationSchemeId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasGradesLoadedInProgramSection(Long sectionId) {
        return gradeItemRepository.existsGradedByProgramSection(sectionId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasGradesLoadedInCourseSection(Long sectionId) {
        return gradeItemRepository.existsGradedByCourseSection(sectionId);
    }
}
