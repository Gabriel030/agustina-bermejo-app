package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.note.domain.CreditableLineFacts;
import com.red_escuela.backend.billing.note.domain.CreditedInvoiceFacts;
import com.red_escuela.backend.billing.note.domain.WithdrawalCancellationItem;
import com.red_escuela.backend.billing.note.domain.WithdrawalLineAmount;
import com.red_escuela.backend.shared.domain.policy.Policy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-204")
@Tag("D-248")
@Tag("D-253")
@Tag("D-236")
@Tag("B2-44")
class WithdrawalReductionPoliciesTest {
    private final Policy<WithdrawalLineReductionContext> unpaid = new WithdrawalReductionInvoiceUnpaidPolicy();
    private final Policy<WithdrawalLineReductionContext> unchanged = new WithdrawalReductionLinesUnchangedPolicy();
    private final Policy<WithdrawalLineReductionContext> leavesDebt = new WithdrawalReductionLeavesDebtPolicy();

    private CreditedInvoiceFacts annual(InvoicePaymentStatus status, InvoiceDocumentStatus document, String paid) {
        List<CreditableLineFacts> lines = IntStream.rangeClosed(1, 10).mapToObj(number -> new CreditableLineFacts(number, ars("80000.00"), ars("0.00"))).toList();
        return new CreditedInvoiceFacts(9L, document, status, ars("800000.00"), ars(paid), "30111222", lines);
    }

    private WithdrawalLineReductionContext contextOf(CreditedInvoiceFacts invoice, List<WithdrawalLineAmount> lines) {
        return new WithdrawalLineReductionContext(WithdrawalCancellationItem.reducing(9L, lines), invoice);
    }

    private List<WithdrawalLineAmount> lines(int from, int to, String amount) {
        List<WithdrawalLineAmount> lines = new ArrayList<>();
        for (int number = from; number <= to; number++) {
            lines.add(new WithdrawalLineAmount(number, ars(amount).value()));
        }
        return lines;
    }

    private boolean denies(Policy<WithdrawalLineReductionContext> policy, WithdrawalLineReductionContext context, String code) {
        var verdict = policy.evaluateRule(context);
        return !verdict.allowed() && code.equals(verdict.code());
    }

    @Test
    void anUnpaidIssuedInvoiceCanBeReduced() {
        assertThat(unpaid.evaluateRule(contextOf(annual(InvoicePaymentStatus.PENDING, InvoiceDocumentStatus.ISSUED, "0.00"), lines(4, 10, "80000.00"))).allowed()).isTrue();
        assertThat(unpaid.evaluateRule(contextOf(annual(InvoicePaymentStatus.OVERDUE, InvoiceDocumentStatus.ISSUED, "0.00"), lines(4, 10, "80000.00"))).allowed()).isTrue();
    }

    @Test
    void anInvoiceThatBecameUnderReviewPaidCancelledOrWithPaymentsIsNotReduced() {
        for (InvoicePaymentStatus status : List.of(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW, InvoicePaymentStatus.PAID, InvoicePaymentStatus.CANCELLED)) {
            assertThat(denies(unpaid, contextOf(annual(status, InvoiceDocumentStatus.ISSUED, "0.00"), lines(4, 10, "80000.00")), "WITHDRAWAL_REDUCTION_INVOICE_NOT_REDUCIBLE"))
                    .as(status.name()).isTrue();
        }
        assertThat(denies(unpaid, contextOf(annual(InvoicePaymentStatus.PENDING, InvoiceDocumentStatus.ISSUED, "10000.00"), lines(4, 10, "80000.00")),
                "WITHDRAWAL_REDUCTION_INVOICE_NOT_REDUCIBLE")).isTrue();
        assertThat(denies(unpaid, contextOf(annual(InvoicePaymentStatus.PENDING, InvoiceDocumentStatus.DRAFT, "0.00"), lines(4, 10, "80000.00")),
                "WITHDRAWAL_REDUCTION_INVOICE_NOT_REDUCIBLE")).isTrue();
    }

    @Test
    void theExpectedAmountsMustMatchWhatIsCreditable() {
        CreditedInvoiceFacts invoice = annual(InvoicePaymentStatus.PENDING, InvoiceDocumentStatus.ISSUED, "0.00");

        assertThat(unchanged.evaluateRule(contextOf(invoice, lines(4, 10, "80000.00"))).allowed()).isTrue();
        assertThat(denies(unchanged, contextOf(invoice, lines(4, 10, "70000.00")), "WITHDRAWAL_REDUCTION_INVOICE_CHANGED")).isTrue();
    }

    @Test
    void aMissingOrRepeatedLineIsRejected() {
        CreditedInvoiceFacts invoice = annual(InvoicePaymentStatus.PENDING, InvoiceDocumentStatus.ISSUED, "0.00");
        List<WithdrawalLineAmount> missing = new ArrayList<>(lines(4, 5, "80000.00"));
        missing.add(new WithdrawalLineAmount(99, ars("80000.00").value()));
        List<WithdrawalLineAmount> repeated = new ArrayList<>(lines(4, 5, "80000.00"));
        repeated.add(new WithdrawalLineAmount(4, ars("80000.00").value()));

        assertThat(denies(unchanged, contextOf(invoice, missing), "WITHDRAWAL_REDUCTION_INVOICE_CHANGED")).isTrue();
        assertThat(denies(unchanged, contextOf(invoice, repeated), "WITHDRAWAL_REDUCTION_INVOICE_CHANGED")).isTrue();
    }

    @Test
    void theReductionMustLeaveTheDebtOfTheAttendedMonths() {
        CreditedInvoiceFacts invoice = annual(InvoicePaymentStatus.PENDING, InvoiceDocumentStatus.ISSUED, "0.00");

        assertThat(leavesDebt.evaluateRule(contextOf(invoice, lines(4, 10, "80000.00"))).allowed()).isTrue();
        assertThat(denies(leavesDebt, contextOf(invoice, lines(1, 10, "80000.00")), "WITHDRAWAL_REDUCTION_WOULD_CLEAR_DEBT")).isTrue();
    }
}
