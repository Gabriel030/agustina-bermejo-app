package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.evaluation.domain.CourseworkOutcome;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.shared.domain.Grade;

import java.util.List;
import java.util.Optional;

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
}
