package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;

public interface SectionEvaluationSetupPort {
    void createInitialEvaluationDraft(SubjectSection section);

    void createInitialEvaluationDraftForCourseSection(CourseSubjectSection section);

    void requireEvaluationSchemeExists(Long evaluationSchemeId);

    boolean hasGradesLoadedInProgramSection(Long sectionId);

    boolean hasGradesLoadedInCourseSection(Long sectionId);
}
