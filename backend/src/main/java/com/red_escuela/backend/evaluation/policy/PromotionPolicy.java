package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.evaluation.domain.CourseworkOutcome;
import com.red_escuela.backend.evaluation.domain.ApprovalRequirement;
import com.red_escuela.backend.evaluation.domain.RequirementGatedOutcome;
import com.red_escuela.backend.evaluation.domain.UnmetApprovalRequirements;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.shared.domain.Grade;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class PromotionPolicy {

    public Optional<CourseworkOutcome> promoteByExamGrades(
            EvaluationScheme scheme, List<GradeItem> exams, boolean allCourseworkLoaded, int examCountExpected) {
        if (!allCourseworkLoaded || examCountExpected == 0 || scheme.getPromotionMinGrade() == null) {
            return Optional.empty();
        }
        if (!CourseworkGrades.allApprovedIndividually(exams, scheme.getPromotionMinGrade())) {
            return Optional.empty();
        }
        Grade average = Grade.average(exams.stream().map(GradeItem::getScore).toList()).orElseThrow();
        return Optional.of(new CourseworkOutcome(EnrollmentStatus.APPROVED, average, FinalGradeType.PROMOTION));
    }

    public Optional<CourseworkOutcome> approveByAggregateGrade(EvaluationScheme scheme, List<GradeItem> items) {
        if (scheme.getMinApprovalGrade() == null) {
            return Optional.empty();
        }
        return CourseworkGrades.aggregate(scheme.getAggregationMethod(), items)
                .filter(aggregate -> aggregate.meets(scheme.getMinApprovalGrade()))
                .map(aggregate -> new CourseworkOutcome(EnrollmentStatus.APPROVED, aggregate, FinalGradeType.PROMOTION));
    }

    public RequirementGatedOutcome restrictPromotionWhenPrerequisitesUnmet(
            Optional<CourseworkOutcome> outcome, BooleanSupplier prerequisitesMet) {
        if (outcome.isEmpty() || !isPromotion(outcome.get()) || prerequisitesMet.getAsBoolean()) {
            return RequirementGatedOutcome.unchanged(outcome);
        }
        return RequirementGatedOutcome.restrictedToFinal(
                new CourseworkOutcome(EnrollmentStatus.REGULAR, null, null), UnmetApprovalRequirements.of(List.of(ApprovalRequirement.PREREQUISITES)));
    }

    public RequirementGatedOutcome holdApprovalUntilRequirementsMet(
            Optional<CourseworkOutcome> outcome, Supplier<UnmetApprovalRequirements> unmetRequirements) {
        if (outcome.isEmpty() || outcome.get().status() != EnrollmentStatus.APPROVED) {
            return RequirementGatedOutcome.unchanged(outcome);
        }
        UnmetApprovalRequirements pendingRequirements = unmetRequirements.get();
        if (pendingRequirements.isEmpty()) {
            return RequirementGatedOutcome.unchanged(outcome);
        }
        return RequirementGatedOutcome.restrictedToFinal(new CourseworkOutcome(EnrollmentStatus.REGULAR, null, null), pendingRequirements);
    }

    private static boolean isPromotion(CourseworkOutcome outcome) {
        return outcome.status() == EnrollmentStatus.APPROVED && outcome.finalGradeType() == FinalGradeType.PROMOTION;
    }
}
