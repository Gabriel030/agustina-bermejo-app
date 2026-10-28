package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.billing.annualupfront.AnnualUpfrontPolicySetsConfiguration;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnnualUpfrontRequestPoliciesTest {

    private static final LocalDate DEADLINE = LocalDate.of(2027, 2, 28);

    private final PolicySet<AnnualUpfrontRequestCheck> policies = new AnnualUpfrontPolicySetsConfiguration().annualUpfrontRequestPolicies();

    private static AnnualUpfrontRequestCheck allowedOn(LocalDate today) {
        return new AnnualUpfrontRequestCheck(today, DEADLINE, false, false, null, false, 10, true);
    }

    private static AnnualUpfrontRequestCheck with(AnnualUpfrontRequestCheck base, boolean scholarship, boolean debt, Long openInvoiceId, boolean monthlyIssued, int lineCount,
                                                  boolean price) {
        return new AnnualUpfrontRequestCheck(base.today(), base.requestDeadline(), scholarship, debt, openInvoiceId, monthlyIssued, lineCount, price);
    }

    private String firstCodeOf(AnnualUpfrontRequestCheck check) {
        PolicyEvaluationResult result = policies.evaluateRulesUntilFirstViolation(check);
        return result.allowed() ? null : result.violations().get(0).code();
    }

    @Test
    @Tag("D-156")
    void theWindowIncludesTheDeadlineDayAndClosesTheNextDay() {
        assertThat(firstCodeOf(allowedOn(LocalDate.of(2027, 2, 27)))).isNull();
        assertThat(firstCodeOf(allowedOn(DEADLINE))).isNull();
        assertThat(firstCodeOf(allowedOn(LocalDate.of(2027, 3, 1)))).isEqualTo("ANNUAL_UPFRONT_REQUEST_WINDOW_CLOSED");
    }

    @Test
    @Tag("D-156")
    void aYearWithoutAnnualUpfrontSettingsIsClosed() {
        AnnualUpfrontRequestCheck withoutDeadline = new AnnualUpfrontRequestCheck(LocalDate.of(2027, 2, 1), null, false, false, null, false, 10, true);

        assertThat(firstCodeOf(withoutDeadline)).isEqualTo("ANNUAL_UPFRONT_REQUEST_WINDOW_CLOSED");
    }

    @Test
    @Tag("D-130")
    @Tag("D-302")
    @Tag("D-388")
    void anActiveScholarshipBlocksTheRequest() {
        assertThat(firstCodeOf(with(allowedOn(LocalDate.of(2027, 2, 1)), true, false, null, false, 10, true))).isEqualTo("ANNUAL_UPFRONT_NOT_AVAILABLE_WITH_SCHOLARSHIP");
        assertThat(firstCodeOf(with(allowedOn(LocalDate.of(2027, 2, 1)), false, false, null, false, 10, true))).isNull();
    }

    @Test
    @Tag("D-152")
    @Tag("D-292")
    void overdueDebtBlocksTheRequest() {
        assertThat(firstCodeOf(with(allowedOn(LocalDate.of(2027, 2, 1)), false, true, null, false, 10, true))).isEqualTo("OVERDUE_DEBT_BLOCKS_ANNUAL_UPFRONT");
    }

    @Test
    @Tag("D-131")
    void anOpenRequestBlocksANewOne() {
        assertThat(firstCodeOf(with(allowedOn(LocalDate.of(2027, 2, 1)), false, false, 77L, false, 10, true))).isEqualTo("ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED");
    }

    @Test
    @Tag("D-131")
    void anAlreadyIssuedMonthlyInstallmentOfTheYearBlocksTheRequest() {
        assertThat(firstCodeOf(with(allowedOn(LocalDate.of(2027, 2, 1)), false, false, null, true, 10, true))).isEqualTo("NO_MONTHS_LEFT_FOR_ANNUAL_UPFRONT");
    }

    @Test
    @Tag("D-131")
    void theInstallmentCountMustFitTheInvoiceLines() {
        AnnualUpfrontRequestCheck base = allowedOn(LocalDate.of(2027, 2, 1));

        assertThat(firstCodeOf(with(base, false, false, null, false, 0, true))).isEqualTo("ANNUAL_UPFRONT_LINE_COUNT_NOT_SUPPORTED");
        assertThat(firstCodeOf(with(base, false, false, null, false, 11, true))).isEqualTo("ANNUAL_UPFRONT_LINE_COUNT_NOT_SUPPORTED");
        assertThat(firstCodeOf(with(base, false, false, null, false, 1, true))).isNull();
        assertThat(firstCodeOf(with(base, false, false, null, false, 10, true))).isNull();
    }

    @Test
    @Tag("D-439")
    void aMissingMonthlyPriceBlocksTheRequest() {
        assertThat(firstCodeOf(with(allowedOn(LocalDate.of(2027, 2, 1)), false, false, null, false, 10, false))).isEqualTo("NO_EFFECTIVE_PRICE_FOR_ANNUAL_UPFRONT");
    }

    @Test
    @Tag("D-156")
    void theFirstViolationFollowsTheDeclaredOrder() {
        AnnualUpfrontRequestCheck everythingWrong = new AnnualUpfrontRequestCheck(LocalDate.of(2027, 3, 1), DEADLINE, true, true, 5L, true, 0, false);

        assertThat(firstCodeOf(everythingWrong)).isEqualTo("ANNUAL_UPFRONT_REQUEST_WINDOW_CLOSED");
        assertThat(firstCodeOf(with(allowedOn(LocalDate.of(2027, 2, 1)), true, true, 5L, true, 0, false))).isEqualTo("ANNUAL_UPFRONT_NOT_AVAILABLE_WITH_SCHOLARSHIP");
        assertThat(firstCodeOf(with(allowedOn(LocalDate.of(2027, 2, 1)), false, true, 5L, true, 0, false))).isEqualTo("OVERDUE_DEBT_BLOCKS_ANNUAL_UPFRONT");
        assertThat(firstCodeOf(with(allowedOn(LocalDate.of(2027, 2, 1)), false, false, 5L, true, 0, false))).isEqualTo("ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED");
        assertThat(firstCodeOf(with(allowedOn(LocalDate.of(2027, 2, 1)), false, false, null, true, 0, false))).isEqualTo("NO_MONTHS_LEFT_FOR_ANNUAL_UPFRONT");
        assertThat(firstCodeOf(with(allowedOn(LocalDate.of(2027, 2, 1)), false, false, null, false, 0, false))).isEqualTo("ANNUAL_UPFRONT_LINE_COUNT_NOT_SUPPORTED");
    }

    @Test
    @Tag("D-156")
    void thePolicySetHasExactlyTheDeclaredRulesInOrder() {
        assertThat(policies.moment()).isEqualTo("ANNUAL_UPFRONT_REQUEST");
        assertThat(policies.policies().stream().map(Policy::ruleName).toList()).isEqualTo(List.of(
                "AnnualUpfrontRequestWindowPolicy",
                "ScholarshipHolderCannotChooseAnnualUpfrontPolicy",
                "AnnualUpfrontBlockedByOverdueDebtPolicy",
                "OnlyOneAnnualUpfrontRequestPolicy",
                "AnnualUpfrontMonthsStillAvailablePolicy",
                "AnnualUpfrontLineCountSupportedPolicy",
                "AnnualUpfrontPriceAvailablePolicy"));
    }
}
