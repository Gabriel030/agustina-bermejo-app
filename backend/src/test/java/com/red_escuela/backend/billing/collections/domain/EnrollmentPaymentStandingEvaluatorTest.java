package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.billing.collections.domain.EnrollmentPaymentEvent.*;
import static com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentPaymentStandingEvaluatorTest {
    private static final LocalDate JUNE_FIRST = LocalDate.of(2027, 6, 1);
    private static final LocalDate MAY_31 = LocalDate.of(2027, 5, 31);
    private static final LocalDate MAY_10 = LocalDate.of(2027, 5, 10);
    private static final EnrollmentSuspensionRule END_OF_MONTH = new EnrollmentSuspensionRule(1, 0);

    private static EnrollmentDebtFacts none(LocalDate cutoff) {
        return new EnrollmentDebtFacts(0, null, 0, 0, 0, cutoff);
    }

    private static EnrollmentDebtFacts installments(int count, LocalDate oldest, LocalDate cutoff) {
        return new EnrollmentDebtFacts(count, oldest, 0, 0, 0, cutoff);
    }

    private static List<EnrollmentPaymentEvent> evaluate(EnrollmentPaymentStatus current, EnrollmentDebtFacts facts) {
        return EnrollmentPaymentStandingEvaluator.evaluateStanding(current, facts, END_OF_MONTH);
    }

    @Test
    @Tag("D-241")
    void anUnpaidInstallmentBecomesOverdueDuringTheMonthAndSuspendsOnlyWhenTheMonthCloses() {
        assertThat(evaluate(UP_TO_DATE, installments(1, MAY_10, null))).containsExactly(OVERDUE_DEBT_DETECTED);
        assertThat(evaluate(OVERDUE, installments(1, MAY_10, null))).isEmpty();
        assertThat(evaluate(OVERDUE, installments(1, MAY_10, MAY_31))).containsExactly(MONTH_CLOSED_WITH_UNPAID_INSTALLMENT);
    }

    @Test
    @Tag("D-241")
    void anEnrollmentFirstSeenOverdueAtMonthCloseGoesThroughOverdueIntoSuspended() {
        assertThat(evaluate(UP_TO_DATE, installments(1, MAY_10, MAY_31)))
                .containsExactly(OVERDUE_DEBT_DETECTED, MONTH_CLOSED_WITH_UNPAID_INSTALLMENT);
    }

    @Test
    @Tag("D-241")
    @Tag("D-292")
    void anUnpaidLateFeeAloneNeverSuspendsButMakesTheEnrollmentOverdue() {
        EnrollmentDebtFacts onlyLateFee = new EnrollmentDebtFacts(0, null, 0, 1, 0, MAY_31);

        assertThat(evaluate(UP_TO_DATE, onlyLateFee)).containsExactly(OVERDUE_DEBT_DETECTED);
        assertThat(evaluate(OVERDUE, onlyLateFee)).isEmpty();
    }

    @Test
    @Tag("D-241")
    void anUnpaidEnrollmentFeeAloneNeverSuspends() {
        EnrollmentDebtFacts onlyEnrollmentFee = new EnrollmentDebtFacts(0, null, 1, 0, 0, MAY_31);

        assertThat(evaluate(OVERDUE, onlyEnrollmentFee)).isEmpty();
    }

    @Test
    @Tag("D-146")
    void anyOtherOverdueInvoiceMakesTheEnrollmentOverdueButDoesNotSuspend() {
        EnrollmentDebtFacts otherInvoice = new EnrollmentDebtFacts(0, null, 0, 0, 1, MAY_31);

        assertThat(evaluate(UP_TO_DATE, otherInvoice)).containsExactly(OVERDUE_DEBT_DETECTED);
        assertThat(evaluate(OVERDUE, otherInvoice)).isEmpty();
    }

    @Test
    @Tag("D-146")
    void anEnrollmentWithoutOverdueInvoicesStaysUpToDate() {
        assertThat(evaluate(UP_TO_DATE, none(MAY_31))).isEmpty();
        assertThat(evaluate(UP_TO_DATE, none(null))).isEmpty();
    }

    @Test
    void anOverdueEnrollmentThatPaidEverythingIsClearedAndNotSuspended() {
        assertThat(evaluate(OVERDUE, none(MAY_31))).containsExactly(OVERDUE_DEBT_CLEARED);
    }

    @Test
    @Tag("D-241")
    @Tag("D-307")
    void aSuspendedEnrollmentStaysSuspendedWhileAnyOverdueInvoiceRemainsLateFeesIncluded() {
        EnrollmentDebtFacts onlyLateFee = new EnrollmentDebtFacts(0, null, 0, 1, 0, null);
        EnrollmentDebtFacts onlyEnrollmentFee = new EnrollmentDebtFacts(0, null, 1, 0, 0, null);
        EnrollmentDebtFacts onlyOther = new EnrollmentDebtFacts(0, null, 0, 0, 1, null);

        assertThat(evaluate(SUSPENDED, onlyLateFee)).isEmpty();
        assertThat(evaluate(SUSPENDED, onlyEnrollmentFee)).isEmpty();
        assertThat(evaluate(SUSPENDED, onlyOther)).isEmpty();
        assertThat(evaluate(SUSPENDED, installments(1, MAY_10, null))).isEmpty();
        assertThat(evaluate(SUSPENDED, none(null))).containsExactly(ACCOUNT_SETTLED);
    }

    @Test
    void aWithdrawnEnrollmentIsNeverReEvaluated() {
        assertThat(evaluate(WITHDRAWN_FOR_NONPAYMENT, none(null))).isEmpty();
        assertThat(evaluate(WITHDRAWN_FOR_NONPAYMENT, installments(2, MAY_10, MAY_31))).isEmpty();
    }

    @Test
    @Tag("D-306")
    void withTwoInstallmentsRequiredASingleUnpaidInstallmentDoesNotSuspend() {
        EnrollmentSuspensionRule twoInstallments = new EnrollmentSuspensionRule(2, 0);

        assertThat(EnrollmentPaymentStandingEvaluator.evaluateStanding(OVERDUE, installments(1, MAY_10, MAY_31), twoInstallments)).isEmpty();
        assertThat(EnrollmentPaymentStandingEvaluator.evaluateStanding(OVERDUE, installments(2, MAY_10, MAY_31), twoInstallments))
                .containsExactly(MONTH_CLOSED_WITH_UNPAID_INSTALLMENT);
    }

    @Test
    @Tag("D-306")
    void withAMinimumOfDaysTheOldestInstallmentMustReachThemAtTheCut() {
        EnrollmentSuspensionRule thirtyDays = new EnrollmentSuspensionRule(1, 30);
        LocalDate twentyNineDaysBeforeTheCut = MAY_31.minusDays(29);
        LocalDate thirtyDaysBeforeTheCut = MAY_31.minusDays(30);

        assertThat(thirtyDays.suspends(installments(1, twentyNineDaysBeforeTheCut, MAY_31))).isFalse();
        assertThat(thirtyDays.suspends(installments(1, thirtyDaysBeforeTheCut, MAY_31))).isTrue();
    }

    @Test
    @Tag("D-306")
    void theRuleNeverSuspendsOutsideTheMonthClose() {
        assertThat(END_OF_MONTH.suspends(installments(5, MAY_10, null))).isFalse();
        assertThat(END_OF_MONTH.suspends(installments(5, MAY_10, MAY_31))).isTrue();
        assertThat(END_OF_MONTH.suspends(installments(5, MAY_10, JUNE_FIRST.minusDays(1)))).isTrue();
    }

    @Test
    void theRuleNeedsAtLeastOneOverdueInstallmentWhateverTheLateFees() {
        assertThat(END_OF_MONTH.suspends(new EnrollmentDebtFacts(0, null, 3, 3, 3, MAY_31))).isFalse();
    }

    @Test
    @Tag("D-306")
    void theRuleIsBuiltFromTheLatePaymentTermsWithTheDefaultsMeaningEndOfMonth() {
        EnrollmentSuspensionRule defaults = EnrollmentSuspensionRule.from(com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms.initial());
        EnrollmentSuspensionRule twoInvoicesAfterTenDays = EnrollmentSuspensionRule.from(new com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms(
                0, com.red_escuela.backend.shared.domain.Percentage.ofFraction(java.math.BigDecimal.ZERO), 10, 2, true, 10, true, true));
        EnrollmentSuspensionRule zeroCount = EnrollmentSuspensionRule.from(new com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms(
                0, com.red_escuela.backend.shared.domain.Percentage.ofFraction(java.math.BigDecimal.ZERO), 0, 0, true, 10, true, true));

        assertThat(defaults).isEqualTo(new EnrollmentSuspensionRule(1, 0));
        assertThat(twoInvoicesAfterTenDays).isEqualTo(new EnrollmentSuspensionRule(2, 10));
        assertThat(zeroCount).isEqualTo(new EnrollmentSuspensionRule(1, 0));
    }

    @Test
    void invalidRulesAndFactsAreRejected() {
        assertThatThrownBy(() -> new EnrollmentSuspensionRule(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrollmentSuspensionRule(1, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrollmentDebtFacts(-1, null, 0, 0, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrollmentDebtFacts(0, null, -1, 0, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrollmentDebtFacts(0, null, 0, -1, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrollmentDebtFacts(0, null, 0, 0, -1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrollmentDebtFacts(1, null, 0, 0, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrollmentDebtFacts(0, MAY_10, 0, 0, 0, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
