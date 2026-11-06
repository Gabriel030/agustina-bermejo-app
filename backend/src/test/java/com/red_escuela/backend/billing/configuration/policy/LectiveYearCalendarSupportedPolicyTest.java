package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;

import static com.red_escuela.backend.billing.configuration.policy.YearChanges.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-524")
class LectiveYearCalendarSupportedPolicyTest {
    private final LectiveYearCalendarSupportedPolicy policy = new LectiveYearCalendarSupportedPolicy();

    private static YearBillingConfigurationChange calendar(YearMonth start, YearMonth end) {
        ProgramYearBillingTemplateSnapshot same = snapshot(1, 1, null, 10);
        return new YearBillingConfigurationChange(same, same, Optional.of(start), Optional.of(end), Set.of(), Set.of());
    }

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluateRule(calendar(YearMonth.of(2027, 4), YearMonth.of(2027, 12)));
        assertThat(denial.allowed()).isFalse();
        assertThat(LectiveYearCalendarSupportedPolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.code()).isEqualTo("LECTIVE_YEAR_CALENDAR_NOT_SUPPORTED");
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void allowsTheDefaultMarchToDecember() {
        assertThat(policy.evaluateRule(calendar(YearMonth.of(2027, 3), YearMonth.of(2027, 12))).allowed()).isTrue();
    }

    @Test
    void allowsFebruaryToDecember() {
        assertThat(policy.evaluateRule(calendar(YearMonth.of(2027, 2), YearMonth.of(2027, 12))).allowed()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 5, 6, 7, 8, 9, 10, 11, 12})
    void deniesAnyOtherStartMonth(int month) {
        assertThat(policy.evaluateRule(calendar(YearMonth.of(2027, month), YearMonth.of(2027, 12))).allowed()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 6, 10, 11})
    void deniesAnEndOtherThanDecember(int month) {
        assertThat(policy.evaluateRule(calendar(YearMonth.of(2027, 3), YearMonth.of(2027, month))).allowed()).isFalse();
    }

    @Test
    void skipsWhenTheTermHasNoDates() {
        assertThat(policy.evaluateRule(withoutDates(snapshot(1, 1, null, 10))).allowed()).isTrue();
    }

    @Test
    void acceptsTheFixtureTermAsTheDefaultMarchToDecember() {
        assertThat(policy.evaluateRule(creation(snapshot(1, 1, null, 10))).allowed()).isTrue();
    }
}
