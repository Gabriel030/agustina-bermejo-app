package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.enrollment.api.CurriculumProgressView;
import com.red_escuela.backend.enrollment.policy.CurriculumSubjectState;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record CurriculumProgressResponse(
        Long programEnrollmentId, String programName, CurriculumSummaryResponse summary, List<CurriculumSubjectResponse> subjects,
        List<CurriculumWarningResponse> warnings) {

    public record CurriculumSummaryResponse(
            @Schema(nullable = true) Percentage completionPercent, int accumulatedCredits, int requiredCredits,
            int remainingCredits, int mandatoryTotal, int mandatoryApproved, CurriculumStateCountsResponse counts,
            List<CurriculumElectiveGroupResponse> electiveGroups) {
    }

    public record CurriculumStateCountsResponse(int approved, int inProgress, int regularPendingFinal, int failed, int available,
                                      int blocked) {
    }

    public record CurriculumSubjectResponse(
            Long programSubjectId, Long subjectId, String subjectCode, String subjectName, int credits,
            @Schema(nullable = true) Integer curricularYear, @Schema(nullable = true) CurricularTerm curricularTerm,
            ProgramSubjectCategory category, @Schema(nullable = true) Long electiveGroupId,
            CurriculumSubjectState state, @Schema(nullable = true) BigDecimal finalScore, int attempts,
            List<CurriculumBlockingPrerequisiteResponse> blockedBy,
            List<CurriculumCorequisiteResponse> corequisites,
            List<CurriculumBlockingPrerequisiteResponse> prerequisites,
            @Schema(nullable = true) Long currentEnrollmentId) {
    }

    public record CurriculumBlockingPrerequisiteResponse(
            Long subjectId, String subjectCode, String subjectName, RequirementType requirementType,
            PrerequisiteStanding standing) {
    }

    public record CurriculumCorequisiteResponse(Long subjectId, String subjectCode, String subjectName,
                                      CurriculumSubjectState state) {
    }

    public record CurriculumElectiveGroupResponse(Long electiveGroupId, String name, int approvedCount, int minRequired,
                                        @Schema(nullable = true) Integer maxAllowed, boolean fulfilled) {
    }

    public record CurriculumWarningResponse(String code, String message) {
    }

    public static CurriculumProgressResponse from(CurriculumProgressView view) {
        CurriculumProgressView.Summary summary = view.summary();
        CurriculumProgressView.StateCounts counts = summary.counts();
        return new CurriculumProgressResponse(view.programEnrollmentId(), view.programName(),
                new CurriculumSummaryResponse(summary.completionPercent(), summary.accumulatedCredits(),
                        summary.requiredCredits(), summary.remainingCredits(), summary.mandatoryTotal(),
                        summary.mandatoryApproved(),
                        new CurriculumStateCountsResponse(counts.approved(), counts.inProgress(), counts.regularPendingFinal(),
                                counts.failed(), counts.available(), counts.blocked()),
                        summary.electiveGroups().stream()
                                .map(group -> new CurriculumElectiveGroupResponse(group.electiveGroupId(),
                                        group.electiveGroupName(), group.approvedCount(), group.minRequired(),
                                        group.maxAllowed(), group.fulfilled()))
                                .toList()),
                view.subjects().stream().map(CurriculumProgressResponse::subjectOf).toList(),
                view.warnings().stream().map(warning -> new CurriculumWarningResponse(warning.code(), warning.message()))
                        .toList());
    }

    private static List<CurriculumBlockingPrerequisiteResponse> prerequisiteResponsesOf(
            List<CurriculumProgressView.BlockingPrerequisite> prerequisites) {
        return prerequisites.stream()
                .map(prerequisite -> new CurriculumBlockingPrerequisiteResponse(prerequisite.subjectId(),
                        prerequisite.subjectCode(), prerequisite.subjectName(), prerequisite.requirementType(),
                        prerequisite.standing()))
                .toList();
    }

    private static CurriculumSubjectResponse subjectOf(CurriculumProgressView.SubjectView subject) {
        return new CurriculumSubjectResponse(subject.programSubjectId(), subject.subjectId(), subject.subjectCode(),
                subject.subjectName(), subject.credits(), subject.curricularYear(), subject.curricularTerm(),
                subject.category(), subject.electiveGroupId(), subject.state(),
                Optional.ofNullable(subject.finalScore()).map(Grade::value).orElse(null), subject.attempts(),
                prerequisiteResponsesOf(subject.blockedBy()),
                subject.corequisites().stream()
                        .map(corequisite -> new CurriculumCorequisiteResponse(corequisite.subjectId(), corequisite.subjectCode(),
                                corequisite.subjectName(), corequisite.state()))
                        .toList(),
                prerequisiteResponsesOf(subject.prerequisites()), subject.currentEnrollmentId());
    }
}
