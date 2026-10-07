package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.Set;

import static com.red_escuela.backend.billing.configuration.policy.YearChanges.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-036")
class IssuedInstallmentsWithinTermRangePolicyTest {
    private final IssuedInstallmentsWithinTermRangePolicy policy = new IssuedInstallmentsWithinTermRangePolicy();

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluate(termRange(YearMonth.of(2027, 3), YearMonth.of(2027, 10), Set.of(YearMonth.of(2027, 11))));
        assertThat(denial.allowed()).isFalse();
        assertThat(IssuedInstallmentsWithinTermRangePolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.code()).isEqualTo("INSTALLMENTS_OUTSIDE_NEW_TERM_RANGE");
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void allowsIssuedPeriodsOnTheExactBounds() {
        assertThat(policy.evaluate(termRange(YearMonth.of(2027, 3), YearMonth.of(2027, 10), Set.of(YearMonth.of(2027, 3), YearMonth.of(2027, 10)))).allowed()).isTrue();
    }

    @Test
    void deniesAnIssuedPeriodBeforeTheNewStart() {
        assertThat(policy.evaluate(termRange(YearMonth.of(2027, 4), YearMonth.of(2027, 10), Set.of(YearMonth.of(2027, 3)))).allowed()).isFalse();
    }

    @Test
    void allowsWhenNothingIsIssued() {
        assertThat(policy.evaluate(termRange(YearMonth.of(2027, 3), YearMonth.of(2027, 4), Set.of())).allowed()).isTrue();
    }
}
