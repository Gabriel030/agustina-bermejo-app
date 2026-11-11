package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.evaluation.domain.CourseworkOutcome;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class CourseworkOutcomeCalculatorTest {

    private final CourseworkOutcomeCalculator calculator = new CourseworkOutcomeCalculator();

    private static Grade grade(String value) {
        return Grade.of(new BigDecimal(value));
    }

    private static GradeItem item(GradeItemType type, int sequence, String score) {
        GradeItem item = new GradeItem();
        item.setType(type);
        item.setSequenceNumber(sequence);
        item.setScore(score == null ? null : grade(score));
        return item;
    }

    private static EvaluationScheme scheme(int exams, int coursework, boolean hasFinal) {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setExamCountExpected(exams);
        scheme.setTpCountExpected(coursework);
        scheme.setHasFinal(hasFinal);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(grade("4"));
        scheme.setMinApprovalGrade(grade("7"));
        scheme.setPromotionMinGrade(grade("7"));
        return scheme;
    }

    private Optional<CourseworkOutcome> calculate(EvaluationScheme scheme, EnrollmentStatus status, GradeItem... items) {
        return calculator.calculate(scheme, List.of(items), status, 1);
    }

    @Test
    void examsAllAtPromotionGradePromoteWithTheDecimalAverage() {
        Optional<CourseworkOutcome> outcome = calculate(scheme(2, 0, true), EnrollmentStatus.IN_PROGRESS,
                item(GradeItemType.EXAM, 1, "7"), item(GradeItemType.EXAM, 2, "8.5"));

        assertThat(outcome).contains(new CourseworkOutcome(EnrollmentStatus.APPROVED, grade("7.75"), FinalGradeType.PROMOTION));
    }

    @Test
    void oneExamBelowPromotionGradeBlocksPromotionAndLeavesTheStudentRegularWhenThereIsAFinal() {
        Optional<CourseworkOutcome> outcome = calculate(scheme(2, 0, true), EnrollmentStatus.IN_PROGRESS,
                item(GradeItemType.EXAM, 1, "6.99"), item(GradeItemType.EXAM, 2, "9"));

        assertThat(outcome).contains(new CourseworkOutcome(EnrollmentStatus.REGULAR, null, null));
    }

    @Test
    void examsBelowTheRegularGradeFailTheStudentWhenThereIsAFinal() {
        Optional<CourseworkOutcome> outcome = calculate(scheme(2, 0, true), EnrollmentStatus.IN_PROGRESS,
                item(GradeItemType.EXAM, 1, "2"), item(GradeItemType.EXAM, 2, "5"));

        assertThat(outcome).contains(new CourseworkOutcome(EnrollmentStatus.FAILED, null, null));
    }

    @Test
    void missingExamsLeaveTheOutcomeUndecided() {
        assertThat(calculate(scheme(2, 0, true), EnrollmentStatus.IN_PROGRESS, item(GradeItemType.EXAM, 1, "9"))).isEmpty();
    }

    @Test
    void failedCourseworkFailsTheStudentOnceEverythingIsLoaded() {
        Optional<CourseworkOutcome> outcome = calculate(scheme(1, 1, true), EnrollmentStatus.IN_PROGRESS,
                item(GradeItemType.EXAM, 1, "9"), item(GradeItemType.TP, 1, "3"));

        assertThat(outcome).contains(new CourseworkOutcome(EnrollmentStatus.FAILED, null, null));
    }

    @Test
    void failedCourseworkDoesNotFailTheStudentWhileExamsAreMissing() {
        assertThat(calculate(scheme(2, 1, true), EnrollmentStatus.IN_PROGRESS, item(GradeItemType.TP, 1, "3"))).isEmpty();
    }

    @Test
    void anApprovedFinalApprovesTheStudentWithTheFinalScore() {
        Optional<CourseworkOutcome> outcome = calculate(scheme(1, 0, true), EnrollmentStatus.REGULAR,
                item(GradeItemType.EXAM, 1, "5"), item(GradeItemType.FINAL, 1, "8"));

        assertThat(outcome).contains(new CourseworkOutcome(EnrollmentStatus.APPROVED, grade("8"), FinalGradeType.FINAL_EXAM));
    }

    @Test
    void aFailedFinalWithNoAttemptsLeftFailsTheStudent() {
        Optional<CourseworkOutcome> outcome = calculate(scheme(1, 0, true), EnrollmentStatus.REGULAR,
                item(GradeItemType.EXAM, 1, "5"), item(GradeItemType.FINAL, 1, "3"));

        assertThat(outcome).contains(new CourseworkOutcome(EnrollmentStatus.FAILED, null, null));
    }

    @Test
    void aFailedFinalWithAttemptsLeftKeepsTheStudentRegular() {
        Optional<CourseworkOutcome> outcome = calculator.calculate(scheme(1, 0, true),
                List.of(item(GradeItemType.EXAM, 1, "5"), item(GradeItemType.FINAL, 1, "3")), EnrollmentStatus.REGULAR, 2);

        assertThat(outcome).isEmpty();
    }

    @Test
    void withoutFinalAnAverageAtTheApprovalGradePromotes() {
        Optional<CourseworkOutcome> outcome = calculate(scheme(2, 0, false), EnrollmentStatus.IN_PROGRESS,
                item(GradeItemType.EXAM, 1, "6"), item(GradeItemType.EXAM, 2, "8"));

        assertThat(outcome).contains(new CourseworkOutcome(EnrollmentStatus.APPROVED, grade("7"), FinalGradeType.PROMOTION));
    }

    @Test
    void withoutFinalAnAverageBetweenRegularAndApprovalLeavesTheOutcomeUndecided() {
        assertThat(calculate(scheme(2, 0, false), EnrollmentStatus.IN_PROGRESS,
                item(GradeItemType.EXAM, 1, "5"), item(GradeItemType.EXAM, 2, "6"))).isEmpty();
    }

    @Test
    void withoutFinalAnAverageBelowTheRegularGradeFails() {
        Optional<CourseworkOutcome> outcome = calculate(scheme(2, 0, false), EnrollmentStatus.IN_PROGRESS,
                item(GradeItemType.EXAM, 1, "2"), item(GradeItemType.EXAM, 2, "3"));

        assertThat(outcome).contains(new CourseworkOutcome(EnrollmentStatus.FAILED, null, null));
    }

    @Test
    void statusesOutsideTheCourseworkFlowProduceNoOutcome() {
        assertThat(calculate(scheme(1, 0, true), EnrollmentStatus.APPROVED, item(GradeItemType.EXAM, 1, "9"))).isEmpty();
    }

    @Test
    void weightedAverageUsesTheItemWeights() {
        EvaluationScheme scheme = scheme(2, 0, false);
        scheme.setAggregationMethod(GradeAggregationMethod.WEIGHTED_AVERAGE);
        GradeItem first = item(GradeItemType.EXAM, 1, "10");
        first.setWeight(0.75);
        GradeItem second = item(GradeItemType.EXAM, 2, "2");
        second.setWeight(0.25);

        Optional<CourseworkOutcome> outcome = calculate(scheme, EnrollmentStatus.IN_PROGRESS, first, second);

        assertThat(outcome).contains(new CourseworkOutcome(EnrollmentStatus.APPROVED, grade("8"), FinalGradeType.PROMOTION));
    }

    @Test
    void holdWhileFreeSuppressesAPassingOutcomeForAFreeStudent() {
        Optional<CourseworkOutcome> passing = Optional.of(new CourseworkOutcome(EnrollmentStatus.REGULAR, null, null));
        BooleanSupplier free = () -> true;

        assertThat(calculator.regularityPolicy().holdWhileFree(passing, EnrollmentStatus.IN_PROGRESS, free)).isEmpty();
        assertThat(calculator.regularityPolicy().holdWhileFree(passing, EnrollmentStatus.REGULAR, free)).isEqualTo(passing);
        Optional<CourseworkOutcome> failing = Optional.of(new CourseworkOutcome(EnrollmentStatus.FAILED, null, null));
        assertThat(calculator.regularityPolicy().holdWhileFree(failing, EnrollmentStatus.IN_PROGRESS, free)).isEqualTo(failing);
        assertThat(calculator.regularityPolicy().holdWhileFree(passing, EnrollmentStatus.IN_PROGRESS, () -> false)).isEqualTo(passing);
        assertThat(calculator.regularityPolicy().holdWhileFree(Optional.empty(), EnrollmentStatus.IN_PROGRESS, free)).isEmpty();
    }
}
