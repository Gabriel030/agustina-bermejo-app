package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.PrerequisiteView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.events.SubjectApprovalStandingChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DependentSubjectApprovalReviewer {

    private static final List<EnrollmentStatus> REVIEWABLE_STATUSES = List.of(
            EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVED);

    private final ProgramCurriculumFinder programCurriculumFinder;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final GradeItemRecorder gradeItemRecorder;

    public DependentSubjectApprovalReviewer(ProgramCurriculumFinder programCurriculumFinder,
                                            SectionEnrollmentFinder sectionEnrollmentFinder,
                                            GradeItemRecorder gradeItemRecorder) {
        this.programCurriculumFinder = programCurriculumFinder;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.gradeItemRecorder = gradeItemRecorder;
    }

    @EventListener
    public void onSubjectApprovalStandingChanged(SubjectApprovalStandingChangedEvent event) {
        for (Long dependentSubjectId : findDependentSubjectIds(event.planId(), event.subjectId())) {
            List<SubjectSectionEnrollment> dependents = sectionEnrollmentFinder.findProgramEnrollmentsOfSubject(
                    event.programEnrollmentId(), dependentSubjectId, REVIEWABLE_STATUSES);
            for (SubjectSectionEnrollment dependent : dependents) {
                gradeItemRecorder.recalculateProgramEnrollmentCondition(dependent.getId());
            }
        }
    }

    private Set<Long> findDependentSubjectIds(Long planId, Long subjectId) {
        Map<Long, List<PrerequisiteView>> prerequisitesByProgramSubject = programCurriculumFinder.findPrerequisitesOfPlan(planId);
        return programCurriculumFinder.findProgramSubjectsOfPlan(planId).stream()
                .filter(programSubject -> prerequisitesByProgramSubject.getOrDefault(programSubject.id(), List.of()).stream()
                        .anyMatch(prerequisite -> subjectId.equals(prerequisite.prerequisiteSubjectId())))
                .map(ProgramSubjectView::subjectId)
                .collect(Collectors.toSet());
    }
}
