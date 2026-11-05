package com.red_escuela.backend.notification.reporting.domain;

import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-357")
class ReminderPaymentGoalTest {

    private static final ReminderPaymentGoal EIGHTY_FIVE = new ReminderPaymentGoal(Percentage.ofPoints(new BigDecimal("85")));

    @Test
    void aValueBelowTheThresholdIsBelowTheGoal() {
        assertThat(EIGHTY_FIVE.evaluate(new BigDecimal("84.99"))).isEqualTo(ReminderGoalEvaluation.BELOW);
    }

    @Test
    void aValueExactlyAtTheThresholdMeetsTheGoal() {
        assertThat(EIGHTY_FIVE.evaluate(new BigDecimal("85.00"))).isEqualTo(ReminderGoalEvaluation.MET);
    }

    @Test
    void aValueAboveTheThresholdMeetsTheGoal() {
        assertThat(EIGHTY_FIVE.evaluate(new BigDecimal("90"))).isEqualTo(ReminderGoalEvaluation.MET);
    }

    @Test
    void aMonthWithoutDataHasNoEvaluation() {
        assertThat(EIGHTY_FIVE.evaluate(null)).isEqualTo(ReminderGoalEvaluation.NO_DATA);
    }

    @Test
    void everyMonthIsEvaluatedOnItsOwn() {
        List<BigDecimal> months = Arrays.asList(new BigDecimal("80"), new BigDecimal("90"), null);

        assertThat(EIGHTY_FIVE.evaluateEach(months)).containsExactly(ReminderGoalEvaluation.BELOW, ReminderGoalEvaluation.MET, ReminderGoalEvaluation.NO_DATA);
    }

    @Test
    void theMetricEvaluatesTheGoalAgainstThePaidPercentageSeriesOnly() {
        ReminderMetricSeries unrelated = new ReminderMetricSeries("sent", ReminderMetricUnit.COUNT, List.of(new BigDecimal("100")));
        ReminderMetricSeries percent = new ReminderMetricSeries("paidWithin72HoursPercent", ReminderMetricUnit.PERCENT, List.of(new BigDecimal("30.00")));

        CollectionReminderMetric metric = new CollectionReminderMetric(List.of(YearMonth.of(2026, 3)), List.of(unrelated, percent), EIGHTY_FIVE);

        assertThat(metric.goalEvaluation()).contains(List.of(ReminderGoalEvaluation.BELOW));
    }

    @Test
    void withoutAConfiguredGoalThereIsNoEvaluation() {
        ReminderMetricSeries percent = new ReminderMetricSeries("paidWithin72HoursPercent", ReminderMetricUnit.PERCENT, List.of(new BigDecimal("30.00")));

        CollectionReminderMetric metric = new CollectionReminderMetric(List.of(YearMonth.of(2026, 3)), List.of(percent), null);

        assertThat(metric.goalEvaluation()).isEmpty();
    }
}
