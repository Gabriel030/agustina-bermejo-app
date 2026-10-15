package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-357")
class BillingDashboardGoalsTest {

    @Test
    void theInitialGoalsOnlyDefineThePaymentOnTimeTargetOfEightyFivePercent() {
        BillingDashboardGoals initial = BillingDashboardGoals.initial();

        assertThat(initial.onTimePaymentAtLeast().points()).isEqualByComparingTo("85");
        assertThat(initial.overdueRatioAtMost()).isNull();
        assertThat(initial.receiptReviewBusinessDaysAtMost()).isNull();
        assertThat(initial.reminderPaidWithin72HoursAtLeast()).isNull();
        assertThat(initial.scope()).isEqualTo(ConfigurationScope.BILLING_DASHBOARD_GOALS);
    }

    @Test
    void theReviewTargetCannotBeLessThanOneBusinessDay() {
        assertThatThrownBy(() -> new BillingDashboardGoals(null, null, 0, null)).isInstanceOf(InvalidConfigurationValuesException.class);
        assertThat(new BillingDashboardGoals(null, null, 1, null).receiptReviewBusinessDaysAtMost()).isEqualTo(1);
    }

    @Test
    void goalsSurviveTheConfigurationJsonCodecIncludingEmptyOnes() {
        BillingDashboardGoals goals = new BillingDashboardGoals(Percentage.ofPoints(new BigDecimal("90")), Percentage.ofPoints(new BigDecimal("12.5")), 3, null);

        assertThat(ConfigurationValuesCodec.fromJson(ConfigurationValuesCodec.toJson(goals))).isEqualTo(goals);
    }

    @Test
    void theGlobalTopicOfTheGoalsUsesTheScopeNameAsKey() {
        assertThat(ConfigurationTopic.ofGlobal(ConfigurationScope.BILLING_DASHBOARD_GOALS).key()).isEqualTo("BILLING_DASHBOARD_GOALS");
    }
}
