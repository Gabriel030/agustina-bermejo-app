package com.red_escuela.backend.billing.reporting.domain;

import com.red_escuela.backend.billing.reporting.exception.UnknownMetricPanelException;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricPanelTest {

    @Test
    void everyPanelIsFoundByItsPath() {
        for (MetricPanel panel : MetricPanel.values()) {
            assertThat(MetricPanel.fromPath(panel.path())).isEqualTo(panel);
        }
        assertThat(MetricPanel.fromPath("on-time-payment")).isEqualTo(MetricPanel.ON_TIME_PAYMENT);
    }

    @Test
    void anUnknownPathIsReportedAsAnUnknownPanel() {
        assertThatThrownBy(() -> MetricPanel.fromPath("collections-stock")).isInstanceOf(UnknownMetricPanelException.class);
    }

    @Test
    void aMetricWithoutGoalHasNoEvaluation() {
        MonthRange range = new MonthRange(YearMonth.of(2026, 3), YearMonth.of(2026, 3));
        MonthlyMetric metric = MonthlyMetric.available(MetricPanel.RECEIPTS, range, List.of());

        assertThat(metric.goalEvaluation()).isEmpty();
        assertThat(metric.withGoal(Optional.empty(), "x")).isSameAs(metric);
        assertThat(MonthlyMetric.unavailable(MetricPanel.LATE_FEES, range).available()).isFalse();
    }

    @Test
    void aMetricEvaluatesItsGoalOverTheNamedSeries() {
        MonthRange range = new MonthRange(YearMonth.of(2026, 3), YearMonth.of(2026, 4));
        MetricGoal goal = new MetricGoal(GoalIndicator.RECEIPT_REVIEW_TIME, new DaysValue(java.math.BigDecimal.valueOf(3)));
        MetricSeries average = new MetricSeries("average", MetricUnit.DAYS,
                java.util.Arrays.asList(new DaysValue(java.math.BigDecimal.valueOf(5)), null));
        MonthlyMetric metric = MonthlyMetric.available(MetricPanel.RECEIPTS, range, List.of(average)).withGoal(Optional.of(goal), "average");

        assertThat(metric.goalEvaluation().orElseThrow()).containsExactly(GoalEvaluation.BELOW, GoalEvaluation.NO_DATA);
        assertThat(metric.withGoal(Optional.of(goal), "missing").goalEvaluation()).isEmpty();
    }
}
