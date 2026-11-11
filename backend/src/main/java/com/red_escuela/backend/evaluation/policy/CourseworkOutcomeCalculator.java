package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.domain.CourseworkOutcome;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class CourseworkOutcomeCalculator {

    private final PromotionPolicy promotionPolicy = new PromotionPolicy();
    private final RegularityPolicy regularityPolicy = new RegularityPolicy();

    public RegularityPolicy regularityPolicy() {
        return regularityPolicy;
    }

    public Optional<CourseworkOutcome> calculate(
            EvaluationScheme scheme, List<GradeItem> items, EnrollmentStatus currentStatus, Integer maxFinalExamAttempts) {
        if (currentStatus != EnrollmentStatus.ENROLLED
                && currentStatus != EnrollmentStatus.IN_PROGRESS
                && currentStatus != EnrollmentStatus.REGULAR) {
            return Optional.empty();
        }

        List<GradeItem> exams = items.stream().filter(i -> i.getType() == GradeItemType.EXAM).toList();
        List<GradeItem> coursework = items.stream().filter(i -> i.getType() == GradeItemType.TP).toList();
        List<GradeItem> finals = items.stream().filter(i -> i.getType() == GradeItemType.FINAL).toList();
        GradeItem lastFinal = finals.stream()
                .max(Comparator.comparing(i -> i.getSequenceNumber() == null ? 0 : i.getSequenceNumber()))
                .orElse(null);

        int examCountExpected = scheme.getExamCountExpected() == null ? 0 : scheme.getExamCountExpected();
        int courseworkCountExpected = scheme.getTpCountExpected() == null ? 0 : scheme.getTpCountExpected();
        int expectedCount = examCountExpected + courseworkCountExpected;
        boolean allCourseworkLoaded = expectedCount > 0 && (exams.size() + coursework.size()) >= expectedCount;

        if (regularityPolicy.failsCourseworkRequirement(scheme, coursework, allCourseworkLoaded, courseworkCountExpected)) {
            return Optional.of(new CourseworkOutcome(EnrollmentStatus.FAILED, null, null));
        }

        Optional<CourseworkOutcome> promotion =
                promotionPolicy.promoteByExamGrades(scheme, exams, allCourseworkLoaded, examCountExpected);
        if (promotion.isPresent()) {
            return promotion;
        }

        if (Boolean.TRUE.equals(scheme.getHasFinal())) {
            return regularityPolicy.evaluateWithFinal(
                    scheme, exams, allCourseworkLoaded, lastFinal, finals.size(), currentStatus, maxFinalExamAttempts);
        }
        return calculateWithoutFinal(scheme, exams, coursework, allCourseworkLoaded);
    }

    private Optional<CourseworkOutcome> calculateWithoutFinal(
            EvaluationScheme scheme, List<GradeItem> exams, List<GradeItem> coursework, boolean allCourseworkLoaded) {
        if (!allCourseworkLoaded) {
            return Optional.empty();
        }
        List<GradeItem> all = new ArrayList<>(exams);
        all.addAll(coursework);
        if (regularityPolicy.failsRegularAverage(scheme, all)) {
            return Optional.of(new CourseworkOutcome(EnrollmentStatus.FAILED, null, null));
        }
        return promotionPolicy.approveByAggregateGrade(scheme, all);
    }
}
