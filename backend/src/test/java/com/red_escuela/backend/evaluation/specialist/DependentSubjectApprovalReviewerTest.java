package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.PrerequisiteView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.events.SubjectApprovalStandingChangedEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DependentSubjectApprovalReviewerTest {

    private static final Long ENROLLMENT_ID = 5L;
    private static final Long ENROLLMENT_PLAN_ID = 17L;
    private static final Long CURRENT_PLAN_ID = 18L;
    private static final Long ALGEBRA_ID = 1L;
    private static final Long ANALYSIS_ID = 3L;
    private static final Long ANALYSIS_ENTRY_ID = 33L;

    private final ProgramCurriculumFinder curriculumFinder = mock(ProgramCurriculumFinder.class);
    private final SectionEnrollmentFinder sectionEnrollmentFinder = mock(SectionEnrollmentFinder.class);
    private final GradeItemRecorder gradeItemRecorder = mock(GradeItemRecorder.class);
    private final DependentSubjectApprovalReviewer reviewer = new DependentSubjectApprovalReviewer(
            curriculumFinder, sectionEnrollmentFinder, gradeItemRecorder);

    private void analysisRequiresAlgebraOnlyInPlan(Long planId) {
        when(curriculumFinder.findProgramSubjectsOfPlan(planId))
                .thenReturn(List.of(new ProgramSubjectView(ANALYSIS_ENTRY_ID, ANALYSIS_ID, "Analisis", 4, 1, null, null)));
        when(curriculumFinder.findPrerequisitesOfPlan(planId)).thenReturn(Map.of(ANALYSIS_ENTRY_ID,
                List.of(new PrerequisiteView(ALGEBRA_ID, "Algebra", RequirementType.APPROVED, "S1", null))));
    }

    private SubjectSectionEnrollment analysisSectionEnrollment() {
        SubjectSectionEnrollment dependent = mock(SubjectSectionEnrollment.class);
        when(dependent.getId()).thenReturn(700L);
        when(sectionEnrollmentFinder.findProgramEnrollmentsOfSubject(eq(ENROLLMENT_ID), eq(ANALYSIS_ID), any()))
                .thenReturn(List.of(dependent));
        return dependent;
    }

    @Test
    void dependentsAreFoundThroughThePlanOfTheEnrollmentEvenWhenTheCurrentPlanDroppedThePrerequisite() {
        analysisRequiresAlgebraOnlyInPlan(ENROLLMENT_PLAN_ID);
        when(curriculumFinder.findProgramSubjectsOfPlan(CURRENT_PLAN_ID)).thenReturn(List.of());
        when(curriculumFinder.findPrerequisitesOfPlan(CURRENT_PLAN_ID)).thenReturn(Map.of());
        analysisSectionEnrollment();

        reviewer.onSubjectApprovalStandingChanged(
                SubjectApprovalStandingChangedEvent.changedFor(ENROLLMENT_ID, ENROLLMENT_PLAN_ID, ALGEBRA_ID, false));

        verify(gradeItemRecorder).recalculateProgramEnrollmentCondition(700L);
    }

    @Test
    void dependentsOfThePrerequisiteAddedOnlyByTheCurrentPlanAreNotReviewed() {
        analysisRequiresAlgebraOnlyInPlan(CURRENT_PLAN_ID);
        when(curriculumFinder.findProgramSubjectsOfPlan(ENROLLMENT_PLAN_ID)).thenReturn(List.of());
        when(curriculumFinder.findPrerequisitesOfPlan(ENROLLMENT_PLAN_ID)).thenReturn(Map.of());
        analysisSectionEnrollment();

        reviewer.onSubjectApprovalStandingChanged(
                SubjectApprovalStandingChangedEvent.changedFor(ENROLLMENT_ID, ENROLLMENT_PLAN_ID, ALGEBRA_ID, false));

        verify(gradeItemRecorder, never()).recalculateProgramEnrollmentCondition(any());
    }
}
