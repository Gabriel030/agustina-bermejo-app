package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;

import static com.red_escuela.backend.billing.configuration.policy.YearChanges.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-036")
@Tag("D-524")
class LectiveYearStartFrozenPolicyTest {
    private final LectiveYearStartFrozenPolicy policy = new LectiveYearStartFrozenPolicy();

    private static YearBillingConfigurationChange change(YearMonth currentStart, YearMonth proposedStart, Set<YearMonth> issued) {
        ProgramYearBillingTemplateSnapshot same = snapshot(1, 1, null, 10);
        return new YearBillingConfigurationChange(same, same, Optional.of(proposedStart), Optional.of(YearMonth.of(2027, 12)), Set.of(), issued, Optional.of(currentStart));
    }

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluateRule(change(YearMonth.of(2027, 3), YearMonth.of(2027, 2), Set.of(YearMonth.of(2027, 3))));
        assertThat(denial.allowed()).isFalse();
        assertThat(LectiveYearStartFrozenPolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.code()).isEqualTo("LECTIVE_YEAR_START_FROZEN");
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void deniesMovingTheStartFromMarchToFebruaryOnceAnInstallmentIsIssued() {
        assertThat(policy.evaluateRule(change(YearMonth.of(2027, 3), YearMonth.of(2027, 2), Set.of(YearMonth.of(2027, 3)))).allowed()).isFalse();
    }

    @Test
    void deniesMovingTheStartFromFebruaryToMarchOnceAnInstallmentIsIssued() {
        assertThat(policy.evaluateRule(change(YearMonth.of(2027, 2), YearMonth.of(2027, 3), Set.of(YearMonth.of(2027, 2)))).allowed()).isFalse();
    }

    @Test
    void allowsMovingTheStartWhileNothingIsIssued() {
        assertThat(policy.evaluateRule(change(YearMonth.of(2027, 3), YearMonth.of(2027, 2), Set.of())).allowed()).isTrue();
    }

    @Test
    void allowsKeepingTheSameStartWithIssuedInstallments() {
        assertThat(policy.evaluateRule(change(YearMonth.of(2027, 3), YearMonth.of(2027, 3), Set.of(YearMonth.of(2027, 3)))).allowed()).isTrue();
    }

    @Test
    void skipsWhenThereIsNoCurrentStart() {
        ProgramYearBillingTemplateSnapshot same = snapshot(1, 1, null, 10);
        YearBillingConfigurationChange creation = new YearBillingConfigurationChange(same, same, Optional.of(YearMonth.of(2027, 2)), Optional.of(YearMonth.of(2027, 12)), Set.of(), Set.of(YearMonth.of(2027, 3)));
        assertThat(policy.evaluateRule(creation).allowed()).isTrue();
    }
}
