package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.enrollment.policy.CurriculumSubjectState;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;

import java.util.List;

public record CurriculumProgressView(
        Long programEnrollmentId, String programName, Summary summary, List<SubjectView> subjects,
        List<Warning> warnings) {

    public record Summary(
            Percentage completionPercent, int accumulatedCredits, int requiredCredits, int remainingCredits,
            int mandatoryTotal, int mandatoryApproved, StateCounts counts,
            List<ElectiveGroupProgressView> electiveGroups) {
    }

    public record StateCounts(int approved, int inProgress, int regularPendingFinal, int failed, int available,
                              int blocked) {
    }

    public record SubjectView(
            Long programSubjectId, Long subjectId, String subjectCode, String subjectName, int credits,
            Integer curricularYear, CurricularTerm curricularTerm, ProgramSubjectCategory category,
            Long electiveGroupId, CurriculumSubjectState state, Grade finalScore, int attempts,
            List<BlockingPrerequisite> blockedBy, List<Corequisite> corequisites) {
    }

    public record BlockingPrerequisite(
            Long subjectId, String subjectCode, String subjectName, RequirementType requirementType,
            PrerequisiteStanding standing) {
    }

    public record Corequisite(Long subjectId, String subjectCode, String subjectName, CurriculumSubjectState state) {
    }

    public record Warning(String code, String message) {
    }
}
