package com.red_escuela.backend.billing.reporting.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricGoalTest {

    private static PercentValue percent(String points) {
        return new PercentValue(Percentage.ofPoints(new BigDecimal(points)));
    }

    private static DaysValue days(String days) {
        return new DaysValue(new BigDecimal(days));
    }

    private final MetricGoal onTimeGoal = new MetricGoal(GoalIndicator.ON_TIME_PAYMENT, percent("85"));

    @Test
    @Tag("D-357")
    void aMonthBelowTheThresholdIsBelowAndAMonthAboveItIsMet() {
        assertThat(onTimeGoal.evaluateEach(List.of(percent("80"), percent("90")))).containsExactly(GoalEvaluation.BELOW, GoalEvaluation.MET);
    }

    @Test
    @Tag("D-357")
    void reachingTheThresholdExactlyMeetsAnAtLeastGoal() {
        assertThat(onTimeGoal.evaluate(percent("85"))).isEqualTo(GoalEvaluation.MET);
        assertThat(onTimeGoal.evaluate(percent("84.99"))).isEqualTo(GoalEvaluation.BELOW);
    }

    @Test
    @Tag("D-357")
    void anAtMostGoalIsMetUpToAndIncludingTheThreshold() {
        MetricGoal reviewGoal = new MetricGoal(GoalIndicator.RECEIPT_REVIEW_TIME, days("3"));

        assertThat(reviewGoal.evaluate(days("3"))).isEqualTo(GoalEvaluation.MET);
        assertThat(reviewGoal.evaluate(days("3.01"))).isEqualTo(GoalEvaluation.BELOW);
        assertThat(reviewGoal.evaluate(days("1"))).isEqualTo(GoalEvaluation.MET);
        assertThat(reviewGoal.comparator()).isEqualTo(GoalComparator.AT_MOST);
    }

    @Test
    @Tag("D-357")
    void aMonthWithoutDataIsNeitherMetNorBelow() {
        assertThat(onTimeGoal.evaluateEach(Arrays.asList(percent("90"), null))).containsExactly(GoalEvaluation.MET, GoalEvaluation.NO_DATA);
    }

    @Test
    void everyIndicatorHasItsOwnComparator() {
        assertThat(GoalIndicator.ON_TIME_PAYMENT.comparator()).isEqualTo(GoalComparator.AT_LEAST);
        assertThat(GoalIndicator.OVERDUE_RATIO.comparator()).isEqualTo(GoalComparator.AT_MOST);
        assertThat(GoalIndicator.RECEIPT_REVIEW_TIME.comparator()).isEqualTo(GoalComparator.AT_MOST);
        assertThat(GoalIndicator.REMINDER_PAYMENT_WITHIN_72H.comparator()).isEqualTo(GoalComparator.AT_LEAST);
    }

    @Test
    void aGoalRejectsAThresholdThatIsNotAPercentageOrDays() {
        assertThatThrownBy(() -> new MetricGoal(GoalIndicator.ON_TIME_PAYMENT, new CountValue(3))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MetricGoal(GoalIndicator.ON_TIME_PAYMENT, new MoneyValue(Money.zero(Currency.ARS)))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparingAValueOfAnotherUnitIsRejected() {
        assertThatThrownBy(() -> onTimeGoal.evaluate(days("3"))).isInstanceOf(IllegalArgumentException.class);
    }
}
