package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.billing.collections.domain.EnrollmentPaymentEvent.*;
import static com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentPaymentStandingEvaluatorTest {
    private static final LocalDate MAY_31 = LocalDate.of(2027, 5, 31);
    private static final LocalDate MAY_10 = LocalDate.of(2027, 5, 10);
    private static final EnrollmentSuspensionRule END_OF_MONTH = new EnrollmentSuspensionRule(1, 0);
    private static final EnrollmentDebtFacts NOTHING_OVERDUE = new EnrollmentDebtFacts(0, 0, 0, 0);
    private static final EnrollmentDebtFacts INSTALLMENT_OVERDUE = new EnrollmentDebtFacts(1, 0, 0, 0);

    private static MonthCloseFacts installmentsAtCutoff(int count, LocalDate oldest) {
        return new MonthCloseFacts(MAY_31, count, oldest);
    }

    private static List<EnrollmentPaymentEvent> evaluate(EnrollmentPaymentStatus current, EnrollmentDebtFacts facts, MonthCloseFacts close) {
        return EnrollmentPaymentStandingEvaluator.evaluateStanding(current, facts, close, END_OF_MONTH);
    }

    @Test
    @Tag("D-241")
    void anUnpaidInstallmentBecomesOverdueDuringTheMonthAndSuspendsOnlyWhenTheMonthCloses() {
        assertThat(evaluate(UP_TO_DATE, INSTALLMENT_OVERDUE, null)).containsExactly(OVERDUE_DEBT_DETECTED);
        assertThat(evaluate(OVERDUE, INSTALLMENT_OVERDUE, null)).isEmpty();
        assertThat(evaluate(OVERDUE, INSTALLMENT_OVERDUE, installmentsAtCutoff(1, MAY_10))).containsExactly(MONTH_CLOSED_WITH_UNPAID_INSTALLMENT);
    }

    @Test
    @Tag("D-241")
    void anEnrollmentFirstSeenOverdueAtMonthCloseGoesThroughOverdueIntoSuspended() {
        assertThat(evaluate(UP_TO_DATE, INSTALLMENT_OVERDUE, installmentsAtCutoff(1, MAY_10)))
                .containsExactly(OVERDUE_DEBT_DETECTED, MONTH_CLOSED_WITH_UNPAID_INSTALLMENT);
    }

    @Test
    @Tag("D-473")
    void aMonthCloseDecidedAtTheCutoffSuspendsEvenIfTheInstallmentWasPaidAfterTheCutoffAndSettlesRightAfter() {
        assertThat(evaluate(OVERDUE, NOTHING_OVERDUE, installmentsAtCutoff(1, MAY_10)))
                .containsExactly(MONTH_CLOSED_WITH_UNPAID_INSTALLMENT, ACCOUNT_SETTLED);
        assertThat(evaluate(UP_TO_DATE, NOTHING_OVERDUE, installmentsAtCutoff(1, MAY_10)))
                .containsExactly(OVERDUE_DEBT_DETECTED, MONTH_CLOSED_WITH_UNPAID_INSTALLMENT, ACCOUNT_SETTLED);
    }

    @Test
    @Tag("D-473")
    void aMonthCloseWithNothingUnpaidAtTheCutoffDoesNotSuspendWhateverTheLiveDebtIs() {
        assertThat(evaluate(OVERDUE, NOTHING_OVERDUE, installmentsAtCutoff(0, null))).containsExactly(OVERDUE_DEBT_CLEARED);
        assertThat(evaluate(OVERDUE, INSTALLMENT_OVERDUE, installmentsAtCutoff(0, null))).isEmpty();
        assertThat(evaluate(UP_TO_DATE, NOTHING_OVERDUE, installmentsAtCutoff(0, null))).isEmpty();
    }

    @Test
    @Tag("D-241")
    @Tag("D-292")
    void anUnpaidLateFeeAloneNeverSuspendsButMakesTheEnrollmentOverdue() {
        EnrollmentDebtFacts onlyLateFee = new EnrollmentDebtFacts(0, 0, 1, 0);

        assertThat(evaluate(UP_TO_DATE, onlyLateFee, installmentsAtCutoff(0, null))).containsExactly(OVERDUE_DEBT_DETECTED);
        assertThat(evaluate(OVERDUE, onlyLateFee, installmentsAtCutoff(0, null))).isEmpty();
    }

    @Test
    @Tag("D-474")
    void anUnpaidEnrollmentFeeAloneMakesTheEnrollmentOverdueButNeverSuspends() {
        EnrollmentDebtFacts onlyEnrollmentFee = new EnrollmentDebtFacts(0, 1, 0, 0);

        assertThat(evaluate(UP_TO_DATE, onlyEnrollmentFee, installmentsAtCutoff(0, null))).containsExactly(OVERDUE_DEBT_DETECTED);
        assertThat(evaluate(OVERDUE, onlyEnrollmentFee, installmentsAtCutoff(0, null))).isEmpty();
    }

    @Test
    @Tag("D-146")
    void anyOtherOverdueInvoiceMakesTheEnrollmentOverdueButDoesNotSuspend() {
        EnrollmentDebtFacts otherInvoice = new EnrollmentDebtFacts(0, 0, 0, 1);

        assertThat(evaluate(UP_TO_DATE, otherInvoice, installmentsAtCutoff(0, null))).containsExactly(OVERDUE_DEBT_DETECTED);
        assertThat(evaluate(OVERDUE, otherInvoice, installmentsAtCutoff(0, null))).isEmpty();
    }

    @Test
    @Tag("D-146")
    void anEnrollmentWithoutOverdueInvoicesStaysUpToDate() {
        assertThat(evaluate(UP_TO_DATE, NOTHING_OVERDUE, installmentsAtCutoff(0, null))).isEmpty();
        assertThat(evaluate(UP_TO_DATE, NOTHING_OVERDUE, null)).isEmpty();
    }

    @Test
    void anOverdueEnrollmentThatPaidEverythingIsClearedAndNotSuspended() {
        assertThat(evaluate(OVERDUE, NOTHING_OVERDUE, null)).containsExactly(OVERDUE_DEBT_CLEARED);
    }

    @Test
    @Tag("D-241")
    @Tag("D-307")
    void aSuspendedEnrollmentStaysSuspendedWhileAnyOverdueInvoiceRemainsLateFeesIncluded() {
        assertThat(evaluate(SUSPENDED, new EnrollmentDebtFacts(0, 0, 1, 0), null)).isEmpty();
        assertThat(evaluate(SUSPENDED, new EnrollmentDebtFacts(0, 1, 0, 0), null)).isEmpty();
        assertThat(evaluate(SUSPENDED, new EnrollmentDebtFacts(0, 0, 0, 1), null)).isEmpty();
        assertThat(evaluate(SUSPENDED, INSTALLMENT_OVERDUE, null)).isEmpty();
        assertThat(evaluate(SUSPENDED, INSTALLMENT_OVERDUE, installmentsAtCutoff(1, MAY_10))).isEmpty();
        assertThat(evaluate(SUSPENDED, NOTHING_OVERDUE, null)).containsExactly(ACCOUNT_SETTLED);
    }

    @Test
    void aWithdrawnEnrollmentIsNeverReEvaluated() {
        assertThat(evaluate(WITHDRAWN_FOR_NONPAYMENT, NOTHING_OVERDUE, null)).isEmpty();
        assertThat(evaluate(WITHDRAWN_FOR_NONPAYMENT, INSTALLMENT_OVERDUE, installmentsAtCutoff(2, MAY_10))).isEmpty();
    }

    @Test
    @Tag("D-306")
    void withTwoInstallmentsRequiredASingleUnpaidInstallmentDoesNotSuspend() {
        EnrollmentSuspensionRule twoInstallments = new EnrollmentSuspensionRule(2, 0);

        assertThat(EnrollmentPaymentStandingEvaluator.evaluateStanding(OVERDUE, INSTALLMENT_OVERDUE, installmentsAtCutoff(1, MAY_10), twoInstallments)).isEmpty();
        assertThat(EnrollmentPaymentStandingEvaluator.evaluateStanding(OVERDUE, INSTALLMENT_OVERDUE, installmentsAtCutoff(2, MAY_10), twoInstallments))
                .containsExactly(MONTH_CLOSED_WITH_UNPAID_INSTALLMENT);
    }

    @Test
    @Tag("D-306")
    void withAMinimumOfDaysTheOldestInstallmentMustReachThemAtTheCutoff() {
        EnrollmentSuspensionRule thirtyDays = new EnrollmentSuspensionRule(1, 30);

        assertThat(thirtyDays.suspends(installmentsAtCutoff(1, MAY_31.minusDays(29)))).isFalse();
        assertThat(thirtyDays.suspends(installmentsAtCutoff(1, MAY_31.minusDays(30)))).isTrue();
    }

    @Test
    @Tag("D-306")
    void theRuleNeedsAtLeastTheRequiredNumberOfOverdueInstallments() {
        assertThat(END_OF_MONTH.suspends(installmentsAtCutoff(0, null))).isFalse();
        assertThat(END_OF_MONTH.suspends(installmentsAtCutoff(1, MAY_10))).isTrue();
    }

    @Test
    @Tag("D-306")
    void theRuleIsBuiltFromTheLatePaymentTermsWithTheDefaultsMeaningEndOfMonth() {
        assertThat(EnrollmentSuspensionRule.from(LatePaymentTerms.initial())).isEqualTo(new EnrollmentSuspensionRule(1, 0));
        assertThat(EnrollmentSuspensionRule.from(termsWith(10, 2))).isEqualTo(new EnrollmentSuspensionRule(2, 10));
        assertThat(EnrollmentSuspensionRule.from(termsWith(0, 0))).isEqualTo(new EnrollmentSuspensionRule(1, 0));
    }

    private static LatePaymentTerms termsWith(int suspensionAfterDays, int suspensionAfterInvoiceCount) {
        return new LatePaymentTerms(0, Percentage.ofFraction(BigDecimal.ZERO), suspensionAfterDays, suspensionAfterInvoiceCount, true, 10, true, true);
    }

    @Test
    void invalidRulesAndFactsAreRejected() {
        assertThatThrownBy(() -> new EnrollmentSuspensionRule(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrollmentSuspensionRule(1, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrollmentDebtFacts(-1, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrollmentDebtFacts(0, -1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrollmentDebtFacts(0, 0, -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrollmentDebtFacts(0, 0, 0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonthCloseFacts(MAY_31, -1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonthCloseFacts(MAY_31, 1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonthCloseFacts(MAY_31, 0, MAY_10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonthCloseFacts(null, 0, null)).isInstanceOf(NullPointerException.class);
    }
}
