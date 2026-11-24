package com.red_escuela.backend.enrollment.policy;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;

import java.time.LocalDate;
import java.util.List;

public record GraduationProjectionInput(
        Long programEnrollmentId, ProgramEnrollmentStatus enrollmentStatus, Integer programTotalYears,
        int closedPeriods, LocalDate today, int accumulatedCredits, int requiredCredits, int remainingCredits,
        int mandatoryTotal, int mandatoryApproved, int approvedSubjects, boolean planChangedAfterEnrollment,
        boolean hasCorequisites, List<ProjectionSubject> subjects, List<ProjectionElectiveGroup> electiveGroups,
        int courseworkCredits) {

    public record ProjectionSubject(
            Long subjectId, int credits, Integer curricularYear, CurricularTerm curricularTerm, Long electiveGroupId,
            CurriculumSubjectState state, List<Long> unmetPrerequisiteSubjectIds) {
    }

    public record ProjectionElectiveGroup(Long electiveGroupId, int minRequired, Integer maxAllowed, boolean fulfilled) {
    }
}
