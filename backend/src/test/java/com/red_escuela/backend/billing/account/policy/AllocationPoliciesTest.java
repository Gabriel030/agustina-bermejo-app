package com.red_escuela.backend.billing.account.policy;

import com.red_escuela.backend.billing.account.domain.OpenInvoiceView;
import com.red_escuela.backend.billing.account.domain.PlannedInvoiceAllocation;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.account.domain.AccountTestData.usd;
import static org.assertj.core.api.Assertions.assertThat;

class AllocationPoliciesTest {

    private static final Long ACCOUNT = 7L;

    private static OpenInvoiceView open(long id, long account, String due, NonNegativeMoney outstanding) {
        return new OpenInvoiceView(id, account, InvoiceDocumentType.INVOICE, null, LocalDate.parse(due), LocalDate.parse("2027-02-01"), outstanding);
    }

    private static PlannedInvoiceAllocation plan(long id, NonNegativeMoney amount) {
        return new PlannedInvoiceAllocation(id, amount);
    }

    private static AllocationPlanContext context(String credit, List<OpenInvoiceView> open, List<PlannedInvoiceAllocation> plan) {
        return new AllocationPlanContext(ACCOUNT, Currency.ARS, ars(credit), open, plan);
    }

    @Test
    @Tag("D-097")
    void sameAccountPolicyAllowsInvoicesOfTheAccountAndCurrency() {
        OpenInvoiceView invoice = open(1, ACCOUNT, "2027-03-10", ars("100.00"));

        PolicyVerdict verdict = new PaymentAllocationSameAccountPolicy().evaluateRule(context("100.00", List.of(invoice), List.of(plan(1, ars("100.00")))));

        assertThat(verdict.allowed()).isTrue();
    }

    @Test
    @Tag("D-097")
    void sameAccountPolicyDeniesAnInvoiceOfAnotherAccount() {
        OpenInvoiceView foreign = open(1, 99L, "2027-03-10", ars("100.00"));

        PolicyVerdict verdict = new PaymentAllocationSameAccountPolicy().evaluateRule(context("100.00", List.of(foreign), List.of(plan(1, ars("100.00")))));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("PAYMENT_ALLOCATION_ACCOUNT_MISMATCH");
    }

    @Test
    @Tag("D-097")
    void sameAccountPolicyDeniesAnInvoiceInAnotherCurrency() {
        OpenInvoiceView dollars = open(1, ACCOUNT, "2027-03-10", usd("100.00"));

        PolicyVerdict verdict = new PaymentAllocationSameAccountPolicy().evaluateRule(context("100.00", List.of(dollars), List.of(plan(1, usd("100.00")))));

        assertThat(verdict.code()).isEqualTo("PAYMENT_ALLOCATION_ACCOUNT_MISMATCH");
    }

    @Test
    @Tag("D-097")
    void sameAccountPolicyDeniesAPlannedAmountInAnotherCurrency() {
        OpenInvoiceView invoice = open(1, ACCOUNT, "2027-03-10", ars("100.00"));

        PolicyVerdict verdict = new PaymentAllocationSameAccountPolicy().evaluateRule(context("100.00", List.of(invoice), List.of(plan(1, usd("100.00")))));

        assertThat(verdict.allowed()).isFalse();
    }

    @Test
    @Tag("D-097")
    void sameAccountPolicyDeniesAnInvoiceThatIsNotOpen() {
        PolicyVerdict verdict = new PaymentAllocationSameAccountPolicy().evaluateRule(context("100.00", List.of(), List.of(plan(1, ars("100.00")))));

        assertThat(verdict.allowed()).isFalse();
    }

    @Test
    @Tag("D-136")
    void fullPaymentPolicyAllowsTheWholeOutstanding() {
        OpenInvoiceView invoice = open(1, ACCOUNT, "2027-03-10", ars("100.00"));

        assertThat(new FullInvoicePaymentOnlyPolicy().evaluateRule(context("100.00", List.of(invoice), List.of(plan(1, ars("100.00"))))).allowed()).isTrue();
        assertThat(new FullInvoicePaymentOnlyPolicy().evaluateRule(context("100.00", List.of(invoice), List.of())).allowed()).isTrue();
    }

    @Test
    @Tag("D-136")
    void fullPaymentPolicyDeniesAPartialAmountByOneCent() {
        OpenInvoiceView invoice = open(1, ACCOUNT, "2027-03-10", ars("100.00"));

        PolicyVerdict verdict = new FullInvoicePaymentOnlyPolicy().evaluateRule(context("100.00", List.of(invoice), List.of(plan(1, ars("99.99")))));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("PARTIAL_PAYMENT_NOT_ALLOWED");
    }

    @Test
    @Tag("D-136")
    void fullPaymentPolicyDeniesAnUnknownInvoice() {
        assertThat(new FullInvoicePaymentOnlyPolicy().evaluateRule(context("100.00", List.of(), List.of(plan(5, ars("100.00"))))).allowed()).isFalse();
    }

    @Test
    @Tag("D-139")
    void orderPolicyAllowsAPlanThatStopsAtTheFirstInvoiceThatDoesNotFit() {
        List<OpenInvoiceView> open = List.of(open(1, ACCOUNT, "2027-03-10", ars("80.00")), open(2, ACCOUNT, "2027-04-10", ars("80.00")));

        assertThat(new OldestOverdueFirstAllocationOrderPolicy().evaluateRule(context("100.00", open, List.of(plan(1, ars("80.00"))))).allowed()).isTrue();
        assertThat(new OldestOverdueFirstAllocationOrderPolicy().evaluateRule(context("160.00", open, List.of(plan(1, ars("80.00")), plan(2, ars("80.00"))))).allowed()).isTrue();
        assertThat(new OldestOverdueFirstAllocationOrderPolicy().evaluateRule(context("50.00", open, List.of())).allowed()).isTrue();
    }

    @Test
    @Tag("D-089")
    void orderPolicyDeniesSkippingTheOldestInvoice() {
        List<OpenInvoiceView> open = List.of(open(1, ACCOUNT, "2027-03-10", ars("80.00")), open(2, ACCOUNT, "2027-04-10", ars("80.00")));

        PolicyVerdict verdict = new OldestOverdueFirstAllocationOrderPolicy().evaluateRule(context("80.00", open, List.of(plan(2, ars("80.00")))));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("ALLOCATION_ORDER_VIOLATED");
    }

    @Test
    @Tag("D-139")
    void orderPolicyDeniesAPlanThatLeavesCreditThatCouldSettleTheNextInvoice() {
        List<OpenInvoiceView> open = List.of(open(1, ACCOUNT, "2027-03-10", ars("80.00")), open(2, ACCOUNT, "2027-04-10", ars("80.00")));

        assertThat(new OldestOverdueFirstAllocationOrderPolicy().evaluateRule(context("160.00", open, List.of(plan(1, ars("80.00"))))).allowed()).isFalse();
    }

    @Test
    @Tag("D-139")
    void orderPolicyDeniesAPlanBeyondTheCredit() {
        List<OpenInvoiceView> open = List.of(open(1, ACCOUNT, "2027-03-10", ars("80.00")));

        assertThat(new OldestOverdueFirstAllocationOrderPolicy().evaluateRule(context("79.99", open, List.of(plan(1, ars("80.00"))))).allowed()).isFalse();
    }

    @Test
    @Tag("D-139")
    void orderPolicyDeniesAPlanWithMoreItemsThanOpenInvoices() {
        List<OpenInvoiceView> open = List.of(open(1, ACCOUNT, "2027-03-10", ars("80.00")));

        assertThat(new OldestOverdueFirstAllocationOrderPolicy().evaluateRule(context("500.00", open, List.of(plan(1, ars("80.00")), plan(2, ars("80.00"))))).allowed()).isFalse();
    }

    @Test
    @Tag("D-094")
    void reversalPolicyAllowsTheFirstReversalAndDeniesTheSecond() {
        assertThat(new PaymentAllocationReversedOnlyOncePolicy().evaluateRule(new AllocationReversalContext(1L, false)).allowed()).isTrue();

        PolicyVerdict denied = new PaymentAllocationReversedOnlyOncePolicy().evaluateRule(new AllocationReversalContext(1L, true));

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.code()).isEqualTo("PAYMENT_ALLOCATION_ALREADY_REVERSED");
    }
}
