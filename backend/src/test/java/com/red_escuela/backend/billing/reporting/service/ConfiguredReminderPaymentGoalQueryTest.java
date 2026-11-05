package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.reporting.domain.DaysValue;
import com.red_escuela.backend.billing.reporting.domain.GoalIndicator;
import com.red_escuela.backend.billing.reporting.domain.MetricGoal;
import com.red_escuela.backend.billing.reporting.domain.PercentValue;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-357")
class ConfiguredReminderPaymentGoalQueryTest {

    private final DashboardGoalsProvider goals = mock(DashboardGoalsProvider.class);
    private final ConfiguredReminderPaymentGoalQuery query = new ConfiguredReminderPaymentGoalQuery(goals);

    @Test
    void aConfiguredGoalIsReturnedAsItsPercentage() {
        Percentage seventy = Percentage.ofPoints(new BigDecimal("70"));
        when(goals.findGoalInEffect(GoalIndicator.REMINDER_PAYMENT_WITHIN_72H))
                .thenReturn(Optional.of(new MetricGoal(GoalIndicator.REMINDER_PAYMENT_WITHIN_72H, new PercentValue(seventy))));

        assertThat(query.findPercentageOfRemindersFollowedByPaymentAtLeast()).contains(seventy);
    }

    @Test
    void withoutAConfiguredGoalThereIsNoPercentage() {
        when(goals.findGoalInEffect(GoalIndicator.REMINDER_PAYMENT_WITHIN_72H)).thenReturn(Optional.empty());

        assertThat(query.findPercentageOfRemindersFollowedByPaymentAtLeast()).isEmpty();
    }

    @Test
    void aGoalThatIsNotAPercentageIsIgnored() {
        when(goals.findGoalInEffect(GoalIndicator.REMINDER_PAYMENT_WITHIN_72H))
                .thenReturn(Optional.of(new MetricGoal(GoalIndicator.RECEIPT_REVIEW_TIME, new DaysValue(BigDecimal.valueOf(3)))));

        assertThat(query.findPercentageOfRemindersFollowedByPaymentAtLeast()).isEmpty();
    }
}
