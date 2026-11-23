package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.enrollment.api.CurriculumProgressView;
import com.red_escuela.backend.enrollment.api.ApprovalSource;
import com.red_escuela.backend.enrollment.policy.CurriculumSubjectState;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record CurriculumProgressResponse(
        Long programEnrollmentId, String programName, CurriculumSummaryResponse summary, List<CurriculumSubjectResponse> subjects,
        List<CurriculumWarningResponse> warnings) {

    public record CurriculumSummaryResponse(
            @Schema(nullable = true) Percentage completionPercent, int accumulatedCredits, int requiredCredits,
            int remainingCredits, int mandatoryTotal, int mandatoryApproved, CurriculumStateCountsResponse counts,
            List<CurriculumElectiveGroupResponse> electiveGroups, int approvedByEquivalence, int creditsByEquivalence,
            @Schema(nullable = true) BigDecimal averageScore, int accumulatedCreditsByCoursework) {
    }

    public record CurriculumStateCountsResponse(int approved, int inProgress, int regularPendingFinal, int failed, int available,
                                      int blocked) {
    }

    public record CurriculumSubjectResponse(
            Long programSubjectId, Long subjectId, String subjectCode, String subjectName, int credits,
            @Schema(nullable = true) Integer curricularYear, @Schema(nullable = true) CurricularTerm curricularTerm,
            ProgramSubjectCategory category, @Schema(nullable = true) Long electiveGroupId,
            CurriculumSubjectState state, @Schema(nullable = true) BigDecimal finalScore, int attempts,
            List<CurriculumBlockingPrerequisiteResponse> blockedBy, List<CurriculumCorequisiteResponse> corequisites,
            @Schema(nullable = true) ApprovalSource approvalSource,
            @Schema(nullable = true) CurriculumEquivalenceResponse equivalence, boolean prerequisitesWaived) {
    }

    public record CurriculumEquivalenceResponse(
            Long id, String originInstitution, String originSubjectName, @Schema(nullable = true) BigDecimal originScore,
            LocalDate recognizedOn, String resolutionNumber) {
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
                                .toList(),
                        summary.approvedByEquivalence(), summary.creditsByEquivalence(),
                        Optional.ofNullable(summary.averageScore()).map(Grade::value).orElse(null),
                        summary.accumulatedCreditsByCoursework()),
                view.subjects().stream().map(CurriculumProgressResponse::subjectOf).toList(),
                view.warnings().stream().map(warning -> new CurriculumWarningResponse(warning.code(), warning.message()))
                        .toList());
    }

    private static CurriculumSubjectResponse subjectOf(CurriculumProgressView.SubjectView subject) {
        return new CurriculumSubjectResponse(subject.programSubjectId(), subject.subjectId(), subject.subjectCode(),
                subject.subjectName(), subject.credits(), subject.curricularYear(), subject.curricularTerm(),
                subject.category(), subject.electiveGroupId(), subject.state(),
                Optional.ofNullable(subject.finalScore()).map(Grade::value).orElse(null), subject.attempts(),
                subject.blockedBy().stream()
                        .map(blocking -> new CurriculumBlockingPrerequisiteResponse(blocking.subjectId(), blocking.subjectCode(),
                                blocking.subjectName(), blocking.requirementType(), blocking.standing()))
                        .toList(),
                subject.corequisites().stream()
                        .map(corequisite -> new CurriculumCorequisiteResponse(corequisite.subjectId(), corequisite.subjectCode(),
                                corequisite.subjectName(), corequisite.state()))
                        .toList(),
                subject.approvalSource(),
                Optional.ofNullable(subject.equivalence()).map(equivalence -> new CurriculumEquivalenceResponse(
                        equivalence.id(), equivalence.originInstitution(), equivalence.originSubjectName(),
                        Optional.ofNullable(equivalence.originScore()).map(Grade::value).orElse(null),
                        equivalence.recognizedOn(), equivalence.resolutionNumber())).orElse(null),
                subject.prerequisitesWaived());
    }
}
