package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-528")
@Tag("D-201")
class AdvanceEnrollmentFeeOpeningPoliciesTest {
    private static final LocalDate CLOSES = LocalDate.of(2027, 2, 28);

    private static ProgramYearBillingTemplateSnapshot opening(LocalDate opensOn) {
        return new ProgramYearBillingTemplateSnapshot(1, 2, null, 10, 1, opensOn);
    }

    private static YearBillingConfigurationChange change(LocalDate current, LocalDate proposed, boolean advanceIssued) {
        return new YearBillingConfigurationChange(opening(current), opening(proposed), Optional.of(YearMonth.of(2027, 3)), Optional.of(YearMonth.of(2027, 12)),
                Set.of(), Set.of(), Optional.of(YearMonth.of(2027, 3)), Optional.of(CLOSES), advanceIssued);
    }

    @Test
    void anOpeningAfterTheWindowClosesIsRejectedWithItsDeclaredCode() {
        PolicyVerdict denial = new AdvanceEnrollmentFeeOpeningNotAfterWindowClosePolicy().evaluateRule(change(null, CLOSES.plusDays(1), false));

        assertThat(denial.allowed()).isFalse();
        assertThat(denial.code()).isEqualTo("ADVANCE_FEE_OPENING_AFTER_WINDOW_CLOSE");
        assertThat(AdvanceEnrollmentFeeOpeningNotAfterWindowClosePolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
    }

    @Test
    void anOpeningOnOrBeforeTheCloseOrNoOpeningAtAllIsAllowed() {
        AdvanceEnrollmentFeeOpeningNotAfterWindowClosePolicy policy = new AdvanceEnrollmentFeeOpeningNotAfterWindowClosePolicy();

        assertThat(policy.evaluateRule(change(null, CLOSES, false)).allowed()).isTrue();
        assertThat(policy.evaluateRule(change(null, LocalDate.of(2026, 9, 1), false)).allowed()).isTrue();
        assertThat(policy.evaluateRule(change(null, null, false)).allowed()).isTrue();
    }

    @Test
    void withoutKnownWindowDatesTheOpeningCannotBeCompared() {
        YearBillingConfigurationChange creation = YearBillingConfigurationChange.forCreation(opening(LocalDate.of(2030, 1, 1)), Optional.empty(), Optional.empty());

        assertThat(new AdvanceEnrollmentFeeOpeningNotAfterWindowClosePolicy().evaluateRule(creation).allowed()).isTrue();
    }

    @Test
    void onceAnAdvanceFeeWasIssuedTheOpeningDateCannotChange() {
        PolicyVerdict denial = new AdvanceEnrollmentFeeOpeningFrozenPolicy().evaluateRule(change(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), true));

        assertThat(denial.allowed()).isFalse();
        assertThat(denial.code()).isEqualTo("ADVANCE_FEE_OPENING_FROZEN");
        assertThat(AdvanceEnrollmentFeeOpeningFrozenPolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
    }

    @Test
    void theOpeningDateChangesFreelyWhenNothingWasIssuedAndStaysWhenUnchanged() {
        AdvanceEnrollmentFeeOpeningFrozenPolicy policy = new AdvanceEnrollmentFeeOpeningFrozenPolicy();

        assertThat(policy.evaluateRule(change(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), false)).allowed()).isTrue();
        assertThat(policy.evaluateRule(change(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), true)).allowed()).isTrue();
        assertThat(policy.evaluateRule(change(null, null, true)).allowed()).isTrue();
        assertThat(policy.evaluateRule(change(null, LocalDate.of(2026, 9, 1), true)).allowed()).isFalse();
    }
}
