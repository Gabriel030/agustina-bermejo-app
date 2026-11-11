package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.evaluation.domain.CourseworkOutcome;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.shared.domain.Grade;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class RegularityPolicy {

    public boolean failsCourseworkRequirement(
            EvaluationScheme scheme, List<GradeItem> coursework, boolean allCourseworkLoaded, int courseworkCountExpected) {
        return allCourseworkLoaded && courseworkCountExpected > 0 && scheme.getMinRegularGrade() != null
                && !CourseworkGrades.allApprovedIndividually(coursework, scheme.getMinRegularGrade());
    }

    public boolean failsRegularAverage(EvaluationScheme scheme, List<GradeItem> items) {
        if (scheme.getMinRegularGrade() == null) {
            return false;
        }
        return CourseworkGrades.aggregate(scheme.getAggregationMethod(), items)
                .map(average -> !average.meets(scheme.getMinRegularGrade()))
                .orElse(true);
    }

    public Optional<CourseworkOutcome> evaluateWithFinal(
            EvaluationScheme scheme, List<GradeItem> exams, boolean allCourseworkLoaded, GradeItem lastFinal,
            int finalAttemptsUsed, EnrollmentStatus currentStatus, Integer maxFinalExamAttempts) {
        boolean regularReached = currentStatus == EnrollmentStatus.REGULAR;

        if (allCourseworkLoaded && scheme.getMinRegularGrade() != null) {
            if (failsRegularAverage(scheme, exams)) {
                return Optional.of(new CourseworkOutcome(EnrollmentStatus.FAILED, null, null));
            }
            regularReached = true;
        }

        if (lastFinal == null || scheme.getMinApprovalGrade() == null) {
            return newlyRegular(regularReached, currentStatus);
        }

        Grade finalScore = lastFinal.getScore();
        if (finalScore != null && finalScore.meets(scheme.getMinApprovalGrade())) {
            return Optional.of(new CourseworkOutcome(EnrollmentStatus.APPROVED, finalScore, FinalGradeType.FINAL_EXAM));
        }

        boolean attemptsLeft = maxFinalExamAttempts == null || finalAttemptsUsed < maxFinalExamAttempts;
        if (attemptsLeft) {
            return newlyRegular(regularReached, currentStatus);
        }
        return Optional.of(new CourseworkOutcome(EnrollmentStatus.FAILED, null, null));
    }

    public Optional<CourseworkOutcome> holdWhileFree(
            Optional<CourseworkOutcome> outcome, EnrollmentStatus currentStatus, BooleanSupplier isFreeByAbsences) {
        if (outcome.isEmpty() || !outcome.get().passesCoursework() || currentStatus == EnrollmentStatus.REGULAR) {
            return outcome;
        }
        return isFreeByAbsences.getAsBoolean() ? Optional.empty() : outcome;
    }

    private Optional<CourseworkOutcome> newlyRegular(boolean regularReached, EnrollmentStatus currentStatus) {
        return regularReached && currentStatus != EnrollmentStatus.REGULAR
                ? Optional.of(new CourseworkOutcome(EnrollmentStatus.REGULAR, null, null))
                : Optional.empty();
    }
}
